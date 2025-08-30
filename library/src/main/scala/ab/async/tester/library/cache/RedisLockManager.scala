package ab.async.tester.library.cache

import akka.actor.ActorSystem
import akka.pattern.after

import javax.inject._
import play.api.{Configuration, Logger}
import redis.clients.jedis.params.SetParams

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

import java.util.{Collections, UUID}
import scala.jdk.CollectionConverters._

object RedisLockManager {
  val DefaultRetryDelay = 500 // milliseconds
}

class RedisClient @Inject()(configuration: Configuration) {
  // Configure Redis connection pool
  private val redisHost = configuration.get[String]("redis.host")
  private val redisPort = configuration.get[Int]("redis.port")
  private val redisPassword = configuration.getOptional[String]("redis.password").filter(_.nonEmpty)

  private val poolConfig = new JedisPoolConfig()
  poolConfig.setMaxTotal(16)
  poolConfig.setMaxIdle(8)
  poolConfig.setMinIdle(2)

  private val jedisPool = new JedisPool(
    poolConfig,
    redisHost,
    redisPort,
    2000, // connection timeout
    redisPassword.orNull
  )

  def getPool: JedisPool = jedisPool
}
/**
 * Generic distributed lock manager using Redis
 * This provides locking capabilities for any resource across multiple application instances
 */
@Singleton
class RedisLockManager @Inject()(redisClient: RedisClient)(implicit ec: ExecutionContext, system: ActorSystem) {
  private val logger = Logger(this.getClass)
  
  // Default lock expiration in seconds
  private val DefaultLockExpiration = 3 * 60 * 1000 // 3 minutes

  private val DefaultMaxRetries = 5
  
  // Unique identifier for this application instance
  private val instanceId = UUID.randomUUID().toString
  private val jedisPool = redisClient.getPool

  // Clean up resources on application shutdown
  sys.addShutdownHook {
    Try(jedisPool.close()).recover {
      case e => logger.error("Error closing Redis pool", e)
    }
  }
  
  /**
   * Creates a namespaced lock key for Redis
   *
   * @param namespace The lock namespace (e.g., "kafka", "http", "db")
   * @param keys The key parts to create a unique lock identifier
   * @return A formatted Redis key
   */
  private def createLockKey(namespace: String, keys: String*): String = {
    if (keys.isEmpty) {
      throw new IllegalArgumentException("At least one key part is required for lock key")
    }
    
    val keyParts = keys.map(_.trim).filter(_.nonEmpty)
    if (keyParts.isEmpty) {
      throw new IllegalArgumentException("All key parts are empty or whitespace")
    }
    
    s"lock:$namespace:${keyParts.mkString(":")}"
  }
  
  /**
   * Convenience method for Kafka consumer locks using groupId and topicName
   */
  private def createKafkaConsumerLockKey(groupId: String, topicName: String): String = {
    createLockKey("kc", groupId, topicName)
  }
  
  /**
   * Acquires a distributed lock with retry mechanism
   *
   * @param lockKey The unique lock key
   * @param expirationMillis Time after which the lock expires automatically (to prevent deadlocks)
   * @param maxRetries Maximum number of retries if lock acquisition fails
   * @param retryDelayMs Delay between retries in milliseconds
   * @return Future[Boolean] that completes with true if lock acquired, false otherwise
   */
  private def acquireLock(
                   lockKey: String,
                   expirationMillis: Int = DefaultLockExpiration,
                   maxRetries: Int = DefaultMaxRetries,
                   retryDelayMs: Int = RedisLockManager.DefaultRetryDelay
  ): Future[Boolean] = {
    val lockValue = s"$instanceId:${System.currentTimeMillis()}"
    
    def tryAcquire(retriesLeft: Int): Future[Boolean] = {
      if (retriesLeft <= 0) {
        logger.warn(s"Failed to acquire Redis lock for $lockKey after maximum retries")
        Future.successful(false)
      } else {
        val acquired = withJedis { jedis =>
          jedis.set(lockKey, lockValue, new SetParams().nx().ex(expirationMillis/1000)) != null
        }
        
        if (acquired) {
          logger.info(s"Successfully acquired Redis lock for $lockKey")
          Future.successful(true)
        } else {
          // Wait before retrying
          after(retryDelayMs.milliseconds, system.scheduler)(tryAcquire(retriesLeft - 1))
        }
      }
    }
    
    tryAcquire(maxRetries)
  }
  
  /**
   * Convenience method to acquire a lock for Kafka consumers
   */
  def acquireKafkaConsumerLock(
    groupId: String, 
    topicName: String, 
    expirationSeconds: Int = DefaultLockExpiration,
    maxRetries: Int = DefaultMaxRetries,
    retryDelayMs: Int = RedisLockManager.DefaultRetryDelay
  ): Future[Boolean] = {
    val lockKey = createKafkaConsumerLockKey(groupId, topicName)
    acquireLock(lockKey, expirationSeconds, maxRetries, retryDelayMs)
  }
  
  /**
   * Releases a previously acquired lock if this instance owns it
   *
   * @param lockKey The unique lock key
   * @return true if the lock was released, false otherwise
   */
  private def releaseLock(lockKey: String): Boolean = {
    withJedis { jedis =>
      // To prevent race conditions, use a Lua script to check and delete
      val script = """
        local lockValue = redis.call("get", KEYS[1])
        if lockValue then
          return redis.call("del", KEYS[1])
        else
          return 0
        end
      """
      
      val keys = Collections.singletonList(lockKey)
      val args = Collections.singletonList(instanceId)
      
      val result = jedis.eval(script, keys, args)
      val released = result != null && result.toString.toInt > 0
      
      if (released) {
        logger.info(s"Released Redis lock for $lockKey")
      } else {
        logger.error(s"Failed to release Redis lock for $lockKey - either expired or owned by another instance")
      }
      
      released
    }
  }
  
  /**
   * Executes a task with a distributed lock
   *
   * @param lockKey The unique lock key
   * @param expirationMillis lock expiration time in milli-seconds
   * @param maxRetries maximum number of retries to acquire the lock
   * @param task the task to execute with the lock
   * @return a future containing the result of the task or a failure if lock acquisition fails
   */
  def withLock[T](
                           lockKey: String,
                           expirationMillis: Int = DefaultLockExpiration,
                           maxRetries: Int = DefaultMaxRetries
  )(task: => Future[T]): Future[T] = {
    
    acquireLock(lockKey, expirationMillis, maxRetries).flatMap { acquired =>
      if (acquired) {
        val taskFuture = Try(task) match {
          case Success(future) => future
          case Failure(e) => 
            releaseLock(lockKey)
            Future.failed(e)
        }
        
        // Ensure lock is released when task completes
        taskFuture.recoverWith {
          case e: Exception =>
            releaseLock(lockKey)
            Future.failed(e)
        }.onComplete { _ =>
          releaseLock(lockKey)
        }
        
        taskFuture
      } else {
        Future.failed(new IllegalStateException(
          s"Failed to acquire lock for key $lockKey after $maxRetries attempts"))
      }
    }
  }
  
  /**
   * Convenience method to execute a task with a Kafka consumer lock
   */
  def withKafkaConsumerLock[T](
                                groupId: String,
                                topicName: String,
                                expirationMillis: Int = DefaultLockExpiration,
                                maxRetries: Int = DefaultMaxRetries
  )(task: => Future[T]): Future[T] = {
    val lockKey = createKafkaConsumerLockKey(groupId, topicName)
    withLock(lockKey, expirationMillis, maxRetries)(task)
  }
  
  /**
   * Helper method to execute Redis operations with resource management
   */
  def withJedis[T](operation: Jedis => T): T = {
    var jedis: Jedis = null
    try {
      jedis = jedisPool.getResource
      operation(jedis)
    } catch {
      case e: Exception =>
        logger.error(s"Redis operation failed: ${e.getMessage}", e)
        throw e
    } finally {
      if (jedis != null) {
        Try(jedis.close()).recover {
          case e => logger.warn(s"Error closing Jedis resource: ${e.getMessage}")
        }
      }
    }
  }
} 