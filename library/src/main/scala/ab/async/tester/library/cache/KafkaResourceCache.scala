package ab.async.tester.library.cache

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import play.api.Logger

import java.util.{Properties, Timer, TimerTask}
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.util.Try
import javax.inject._

/**
 * Cache for Kafka producers and consumers to avoid creating new connections for each request
 */
@Singleton
class KafkaResourceCache @Inject()() {
  private val logger = Logger(this.getClass)
  
  // Cache expiration time
  private val CacheExpirationTime = 3.minutes.toMillis
  
  // Cache for producers and consumers
  private val producerCache = new TrieMap[String, CachedProducer]()
  private val consumerCache = new TrieMap[String, CachedConsumer]()
  
  // Timer for cache cleanup
  private val cleanupTimer = new Timer("kafka-resource-cleanup", true)
  
  // Schedule periodic cleanup to prevent memory leaks
  cleanupTimer.scheduleAtFixedRate(new TimerTask {
    override def run(): Unit = {
      try {
        cleanupExpiredResources()
      } catch {
        case e: Exception => 
          logger.error("Error cleaning up Kafka resource cache", e)
      }
    }
  }, 30000, 30000)
  
  /**
   * Get or create a Kafka producer for a resource
   *
   * @param resourceId The resource ID
   * @param createProducer Function to create a new producer if not in cache
   * @return A Kafka producer
   */
  def getOrCreateProducer(resourceId: String, createProducer: => KafkaProducer[String, String]): KafkaProducer[String, String] = {
    val now = System.currentTimeMillis()
    
    // Get from cache or create new
    val cachedProducer = producerCache.getOrElseUpdate(resourceId, {
      logger.info(s"Creating new Kafka producer for resource: $resourceId")
      CachedProducer(createProducer, now)
    })
    
    // Update last access time
    cachedProducer.lastAccessTime = now
    
    cachedProducer.producer
  }
  
  /**
   * Get or create a Kafka consumer for a resource and group
   *
   * @param resourceId The resource ID
   * @param groupId The consumer group ID
   * @param createConsumer Function to create a new consumer if not in cache
   * @return A Kafka consumer
   */
  def getOrCreateConsumer(resourceId: String, groupId: String, topicName: String, createConsumer: => KafkaConsumer[String, String]): KafkaConsumer[String, String] = {
    val now = System.currentTimeMillis()
    val cacheKey = s"$resourceId:$groupId$topicName"
    
    // Get from cache or create new
    val cachedConsumer = consumerCache.getOrElseUpdate(cacheKey, {
      logger.info(s"Creating new Kafka consumer for resource: $resourceId, group: $groupId")
      CachedConsumer(createConsumer, now)
    })
    
    // Update last access time
    cachedConsumer.lastAccessTime = now
    
    cachedConsumer.consumer
  }
  
  /**
   * Removes expired resources from the cache
   */
  private def cleanupExpiredResources(): Unit = {
    val now = System.currentTimeMillis()
    
    // Clean up producers
    val expiredProducers = producerCache.filter { case (_, cached) => 
      now - cached.lastAccessTime > CacheExpirationTime 
    }.keys
    
    expiredProducers.foreach { key =>
      producerCache.remove(key).foreach { cached =>
        logger.info(s"Removing expired Kafka producer for resource: $key")
        Try(cached.producer.close()).recover {
          case e => logger.warn(s"Error closing Kafka producer: ${e.getMessage}")
        }
      }
    }
    
    // Clean up consumers
    val expiredConsumers = consumerCache.filter { case (_, cached) => 
      now - cached.lastAccessTime > CacheExpirationTime 
    }.keys
    
    expiredConsumers.foreach { key =>
      consumerCache.remove(key).foreach { cached =>
        logger.info(s"Removing expired Kafka consumer for key: $key")
        Try(cached.consumer.close()).recover {
          case e => logger.warn(s"Error closing Kafka consumer: ${e.getMessage}")
        }
      }
    }
    
//    logger.debug(s"Cache stats - Producers: ${producerCache.size}, Consumers: ${consumerCache.size}")
  }
  
  /**
   * Forcefully shuts down all cached resources
   * This should be called when the application is shutting down
   */
  def shutdownAll(): Unit = {
    logger.info(s"Shutting down all Kafka resources (producers: ${producerCache.size}, consumers: ${consumerCache.size})")
    
    // Cancel the cleanup timer
    cleanupTimer.cancel()
    
    // Close all producers
    producerCache.foreach { case (key, cached) =>
      Try(cached.producer.close()).recover {
        case e => logger.warn(s"Error closing Kafka producer ($key): ${e.getMessage}")
      }
    }
    producerCache.clear()
    
    // Close all consumers
    consumerCache.foreach { case (key, cached) =>
      Try(cached.consumer.close()).recover {
        case e => logger.warn(s"Error closing Kafka consumer ($key): ${e.getMessage}")
      }
    }
    consumerCache.clear()
    
    logger.info("All Kafka resources have been closed")
  }
  
  // Cached resource case classes
  private case class CachedProducer(producer: KafkaProducer[String, String], var lastAccessTime: Long)
  private case class CachedConsumer(consumer: KafkaConsumer[String, String], var lastAccessTime: Long)
} 