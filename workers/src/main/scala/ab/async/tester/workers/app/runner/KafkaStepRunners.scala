package ab.async.tester.workers.app.runner

import ab.async.tester.domain.clients.kafka.KafkaConfig
import ab.async.tester.domain.enums.StepStatus
import ab.async.tester.domain.resource.KafkaResourceConfig
import ab.async.tester.domain.step.{FlowStep, KafkaMessage, KafkaMessagesResponse, KafkaPublishMeta, KafkaSubscribeMeta, StepError, StepResponse}
import ab.async.tester.library.cache.{KafkaResourceCache, RedisLockManager}
import ab.async.tester.library.clients.events.KafkaClient
import ab.async.tester.library.repository.resource.ResourceRepository
import com.google.inject.{Inject, Singleton}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer

import java.time.Duration
import java.util.{Properties, UUID}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.Breaks.{break, breakable}


@Singleton
class KafkaPublisherStepRunner @Inject()(
  implicit ec: ExecutionContext, 
  resourceRepository: ResourceRepository,
  kafkaResourceCache: KafkaResourceCache,
  kafkaClient: KafkaClient
) extends BaseStepRunner {
  override protected val runnerName: String = "KafkaPublisherStepRunner"
  
  override protected def executeStep(step: FlowStep, previousResults: List[StepResponse]): Future[StepResponse] = {
    val kafkaMeta = step.meta match {
      case meta: KafkaPublishMeta => meta
      case _ =>
        throw new IllegalStateException(s"Invalid step meta found for publishing kafka message for step ${step.name}")
    }

    val resourceId = kafkaMeta.resourceId
    resourceRepository.findById(resourceId).map { resource =>
      try {
        val kafkaConfig = resource match {
          case Some(kafkaConfig: KafkaResourceConfig) => kafkaConfig
          case None =>
            throw new IllegalStateException(s"The resource for step: ${step.name} is not found")
          case _ =>
            throw new IllegalStateException(s"The resourceId is not valid for step: ${step.name}")
        }
        if (kafkaConfig.brokerList.isEmpty) {
          throw new IllegalArgumentException("Bootstrap servers are required for Kafka publisher")
        }

        if (kafkaMeta.topicName.isEmpty) {
          throw new IllegalArgumentException("Topic name is required for Kafka publisher")
        }

        if (kafkaMeta.messages.isEmpty) {
          throw new IllegalArgumentException("At least one message is required for Kafka publisher")
        }

        // Create Kafka producer configuration
        val kc = KafkaConfig(
          bootstrapServers = kafkaConfig.brokerList,
          otherConfig = kafkaConfig.config.getOrElse(Map.empty)
        )

        // Get producer from cache or create new
        val producer = kafkaResourceCache.getOrCreateProducer(resourceId, kafkaClient.getKafkaPublisher(kc))

        try {
          kafkaMeta.messages.foreach { msg =>
            val record = new ProducerRecord[String, String](kafkaMeta.topicName, msg.key.getOrElse(""), msg.value)
            producer.send(record).get()
          }

          // Success response
          StepResponse(
            name = step.name,
            id = step.id.getOrElse(""),
            status = StepStatus.SUCCESS,
            response = KafkaMessagesResponse(kafkaMeta.messages)
          )
        } catch {
          case e: Exception =>
            logger.error(s"Error in Kafka publisher step ${step.name}: ${e.getMessage}", e)
            throw e  // Re-throw to be handled in the outer catch block
        }
      } catch {
        case e: Exception =>
          logger.error(s"Error in Kafka publisher step ${step.name}: ${e.getMessage}", e)
          StepResponse(
            name = step.name,
            id = step.id.getOrElse(""),
            status = StepStatus.ERROR,
            response = StepError(error = e.getMessage, expectedValue = None, actualValue = None)
          )
      }
    }
  }
}


@Singleton
class KafkaConsumerStepRunner @Inject()(
  implicit ec: ExecutionContext, 
  resourceRepository: ResourceRepository,
  kafkaResourceCache: KafkaResourceCache,
  redisLockManager: RedisLockManager
) extends BaseStepRunner {
  override protected val runnerName: String = "KafkaConsumerStepRunner"
  
  // Keep track of running background consumers by step ID (now only tracking threads)
  private val backgroundThreads = scala.collection.mutable.Map[String, Thread]()

  override protected def executeStep(step: FlowStep, previousResults: List[StepResponse]): Future[StepResponse] = {
    val kafkaMeta = step.meta match {
      case meta: KafkaSubscribeMeta => meta
      case _ =>
        throw new IllegalStateException(s"Invalid step meta found for subscribing kafka message for step ${step.name}")
    }

    val resourceId = kafkaMeta.resourceId
    
    resourceRepository.findById(resourceId).flatMap { resource =>
      val kafkaConfig = resource match {
        case Some(kafkaConfig: KafkaResourceConfig) => kafkaConfig
        case None =>
          return Future.failed(new IllegalStateException(s"The resource for step: ${step.name} is not found"))
        case _ =>
          return Future.failed(new IllegalStateException(s"The resourceId is not valid for step: ${step.name}"))
      }

      if (kafkaConfig.brokerList.isEmpty) {
        return Future.failed(new IllegalArgumentException("Bootstrap servers are required for Kafka consumer"))
      }

      if (kafkaMeta.topicName.isEmpty) {
        return Future.failed(new IllegalArgumentException("Topic name is required for Kafka consumer"))
      }

      // Check if this step is set to run in background
      if (step.runInBackground) {
        // For background steps, we need to use Redis distributed locks
        // to ensure only one instance with the same group ID and topic is running
        startBackgroundConsumer(step, kafkaMeta, kafkaConfig)
      } else {
        // For regular steps, use a distributed lock to prevent concurrent consumption
        // from the same group ID and topic
        redisLockManager.withKafkaConsumerLock(kafkaMeta.groupId, kafkaMeta.topicName, step.timeoutMs, step.timeoutMs / RedisLockManager.DefaultRetryDelay) {
          regularConsumer(step, kafkaMeta, kafkaConfig)
        }
      }
    }
  }
  
  private def startBackgroundConsumer(step: FlowStep, kafkaMeta: KafkaSubscribeMeta, kafkaConfig: KafkaResourceConfig): Future[StepResponse] = {
    logger.info(s"Starting background Kafka consumer for step ${step.name} on topic ${kafkaMeta.topicName}")
    
    // Create a promise that will be completed when the background consumer is ready
    val promise = Promise[StepResponse]()
    val stepId = step.id.getOrElse(UUID.randomUUID().toString)
    
    // Use the Redis lock manager to ensure only one instance of a background consumer 
    // with the same group ID and topic can run at a time
    redisLockManager.withKafkaConsumerLock(kafkaMeta.groupId, kafkaMeta.topicName, step.timeoutMs, step.timeoutMs / RedisLockManager.DefaultRetryDelay) {
    
      // Create consumer configuration
      val props = createConsumerProps(kafkaMeta, kafkaConfig)
      
      // No need to generate a unique group ID since we're using locks to ensure exclusivity
      // This allows us to keep the original user-specified group ID
      props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaMeta.groupId)
      
      // Create a new consumer for background operation
      val consumer = new KafkaConsumer[String, String](props)
      
      // Subscribe to the topic
      consumer.subscribe(List(kafkaMeta.topicName).asJava)
      
      // Create a daemon thread that will run until the flow completes
      val consumerThread = new Thread(() => {
        val messages = ArrayBuffer[KafkaMessage]()
        val startTime = System.currentTimeMillis()
        try {
          while (!Thread.currentThread().isInterrupted) {
            val records = consumer.poll(Duration.ofMillis(500))

            for (record <- records.asScala) {
              val message = KafkaMessage(
                key = Option(record.key()),
                value = Option(record.value()).getOrElse("")
              )
              
              messages += message
              
              // Log periodically if we get more messages
              if (messages.size % 10 == 0) {
                logger.debug(s"Background Kafka consumer for step ${step.name} has received ${messages.size} messages so far")
              }
              
              // If we reach max messages, we stop (if specified)
              if (kafkaMeta.maxMessages > 0 && messages.size >= kafkaMeta.maxMessages) {
                logger.info(s"Background Kafka consumer for step ${step.name} reached max messages: ${messages.size}")
                Thread.currentThread().interrupt()
                val response = StepResponse(
                  name = step.name,
                  id = stepId,
                  status = StepStatus.SUCCESS,
                  response = KafkaMessagesResponse(messages = messages.toList)
                )

                promise.success(response)
                break
              }
            }

            if (System.currentTimeMillis() - startTime > step.timeoutMs && !promise.isCompleted) {
              logger.info(s"Background Kafka consumer for step ${step.name} reached timeout with messages count: ${messages.size}")
              val response = StepResponse(
                name = step.name,
                id = stepId,
                status = StepStatus.SUCCESS,
                response = KafkaMessagesResponse(messages = messages.toList)
              )

              promise.success(response)
              Thread.currentThread().interrupt()
              break
            }
            
            // Commit offsets
            if (records.count() > 0) {
              consumer.commitSync()
            }
          }
        } catch {
          case e: InterruptedException =>
            logger.info(s"Background Kafka consumer for step ${step.name} was interrupted")
          case e: Exception =>
            logger.error(s"Error in background Kafka consumer for step ${step.name}: ${e.getMessage}", e)
            if (!promise.isCompleted) {
              promise.failure(e)
            }
        } finally {
          try {
            consumer.close()
          } catch {
            case e: Exception =>
              logger.warn(s"Error closing Kafka consumer: ${e.getMessage}")
          }

          // Remove from tracking map and release the Redis lock
          synchronized {
            backgroundThreads.remove(stepId)
            logger.info(s"Released lock for background consumer group ${kafkaMeta.groupId} on topic ${kafkaMeta.topicName}")
          }
          
          logger.info(s"Background Kafka consumer for step ${step.name} has stopped after processing ${messages.size} messages")
        }
      })
      
      // Set as daemon so it doesn't prevent app shutdown
      consumerThread.setDaemon(true)
      consumerThread.setName(s"kafka-consumer-${step.name}-${stepId}")
      
      // Track the thread
      synchronized {
        backgroundThreads.put(stepId, consumerThread)
      }
      
      // Start the thread
      consumerThread.start()
      
      promise.future
    }
  }
  
  private def regularConsumer(step: FlowStep, kafkaMeta: KafkaSubscribeMeta, kafkaConfig: KafkaResourceConfig): Future[StepResponse] = {
    val stepId = step.id.getOrElse(UUID.randomUUID().toString)
    
    Future {
      val props = createConsumerProps(kafkaMeta, kafkaConfig)
      
      // Fetch consumer from cache
      val consumer = kafkaResourceCache.getOrCreateConsumer(
        kafkaMeta.resourceId,
        kafkaMeta.groupId,
        kafkaMeta.topicName,
        new KafkaConsumer[String, String](props)
      )

      try {
        // Reset subscription in case it was used for a different topic before
        consumer.unsubscribe()
        consumer.subscribe(List(kafkaMeta.topicName).asJava)

        // Poll for messages
        val messages = ArrayBuffer[KafkaMessage]()
        val startTime = System.currentTimeMillis()
        val timeout = step.timeoutMs

        // Continue until we get maximum messages or time out
        while (messages.size < kafkaMeta.maxMessages &&
          (System.currentTimeMillis() - startTime) < timeout) {

          val records = consumer.poll(Duration.ofMillis(500))
          breakable {
            for(record <- records.asScala) {
              messages += KafkaMessage(
                key = Option(record.key()),
                value = Option(record.value()).getOrElse("")
              )

              if (messages.size >= kafkaMeta.maxMessages) {
                consumer.commitSync()
                logger.debug(s"Reached maximum messages (${messages.size})")
                break
              }
            }
          }
        }

        // Success response with messages
        StepResponse(
          name = step.name,
          id = stepId,
          status = StepStatus.SUCCESS,
          response = KafkaMessagesResponse(messages = messages.toList)
        )
      } finally {
        Try(consumer.commitSync())
      }
    }
  }
  
  private def createConsumerProps(kafkaMeta: KafkaSubscribeMeta, kafkaConfig: KafkaResourceConfig): Properties = {
    val props = new Properties()
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.brokerList)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaMeta.groupId)
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    
    // For background consumers, we might want different settings
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, if (kafkaMeta.fromBeginning) "earliest" else "latest")
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    
    // Apply any additional config from the resource
    kafkaConfig.config.map(config =>
      config.map {
        case (k, v) => props.put(k, v)
      })
      
    props
  }
  
  // Method to stop a background consumer - could be called when cleaning up flows
  def stopBackgroundConsumer(stepId: String): Unit = {
    synchronized {
      backgroundThreads.get(stepId).foreach { thread =>
        logger.info(s"Stopping background Kafka consumer thread for step ID: $stepId")
        thread.interrupt()
      }
    }
  }
} 