package ab.async.tester.service.flows

import ab.async.tester.constants.Constants
import ab.async.tester.domain.clients.kafka.KafkaConfig
import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.enums.StepStatus.IN_PROGRESS
import ab.async.tester.domain.execution.{Execution, ExecutionStep}
import ab.async.tester.domain.flow.Floww
import ab.async.tester.domain.requests.RunFlowRequest
import ab.async.tester.library.cache.KafkaResourceCache
import ab.async.tester.library.clients.events.KafkaClient
import ab.async.tester.library.clients.redis.RedisPubSubService
import ab.async.tester.library.repository.execution.ExecutionRepository
import akka.NotUsed
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.Source
import com.google.inject.{Inject, Singleton}
import com.typesafe.config.Config
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.apache.kafka.clients.producer.ProducerRecord
import play.api.Configuration

import java.time.Instant
import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters._

@Singleton
class FlowExecutionServiceImpl @Inject()(
                                executionRepository: ExecutionRepository,
                                redisPubSubService: RedisPubSubService,
                                kafkaResourceCache: KafkaResourceCache,
                                configuration: Configuration,
                                kafkaClient: KafkaClient,
                                flowService: FlowServiceTrait
                              )(implicit ec: ExecutionContext, mat: Materializer) extends FlowExecutionService {

  private val kafkaConfig = {
    val conf = configuration.get[Config]("kafka")
    KafkaConfig(
      bootstrapServers = conf.getString("bootstrapServers"),
    )
  }
  private val workerQueueTopic = configuration.get[String]("events.workerQueueTopic")

  override def createExecution(runRequest: RunFlowRequest): Future[Execution] = {
    val executionId = java.util.UUID.randomUUID().toString

    for {
      flow <- flowService.getFlow(runRequest.flowId).map(_.get) // TODO handle not found
      execution <- {
        val execution = createExecutionEntity(executionId, flow, runRequest)
        executionRepository.saveExecution(execution).map(_ => execution)
      }
    } yield {
      // Publish to Kafka for workers to pick up
      val kafkaPublisher = kafkaResourceCache.getOrCreateProducer(Constants.SystemKafkaResourceId, kafkaClient.getKafkaPublisher(kafkaConfig))
      val message = execution.asJson.noSpaces
      val record = new ProducerRecord[String, String](workerQueueTopic, execution.id, message)

      // Send message asynchronously and log the result
      val sendFuture = Future { kafkaPublisher.send(record).get() }
      sendFuture.onComplete {
        case scala.util.Success(metadata) =>
          println(s"✅ Message sent successfully to topic: ${metadata.topic()}, partition: ${metadata.partition()}, offset: ${metadata.offset()}")
          println(s"📝 Message content: $message")
        case scala.util.Failure(exception) =>
          println(s"❌ Failed to send message to Kafka: ${exception.getMessage}")
          exception.printStackTrace()
      }

      // Return the execution instance directly
      execution
    }
  }

  override def streamExecutionUpdates(executionId: String): Source[String, NotUsed] = {
    val (queue, source) = Source.queue[Json](64, OverflowStrategy.dropHead).preMaterialize()

    // Register queue for Redis updates
    redisPubSubService.registerQueue(executionId, queue)

    // Map Json to String for WebSocket
    source.map(_.noSpaces)
  }

  override def stopExecutionStream(executionId: String): Unit = {
    redisPubSubService.unregisterQueue(executionId)
  }


  private def createExecutionEntity(executionId: String, flow: Floww, runFlowRequest: RunFlowRequest) = {
    val now = Instant.now()
    val execSteps = flow.steps.map { step =>
      ExecutionStep(
        id = step.id,
        name = step.name,
        stepType = step.stepType,
        meta = step.meta,
        timeoutMs = step.timeoutMs,
        runInBackground = step.runInBackground,
        continueOnSuccess = step.continueOnSuccess,
        status = IN_PROGRESS,
        startedAt = now,
        logs = List.empty,
        response = None
      )
    }
    Execution(
      id = executionId,
      flowId = flow.id.get,
      flowVersion = flow.version,
      status = ExecutionStatus.Todo,
      startedAt = now,
      steps = execSteps,
      updatedAt = now,
      parameters = Option(runFlowRequest.params)
    )
  }

}
