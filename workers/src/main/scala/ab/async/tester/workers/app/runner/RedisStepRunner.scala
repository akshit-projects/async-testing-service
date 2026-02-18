package ab.async.tester.workers.app.runner

import ab.async.tester.domain.execution.ExecutionStep
import ab.async.tester.domain.resource.CacheConfig
import ab.async.tester.domain.step.metas.{RedisOperation, RedisStepMeta}
import ab.async.tester.domain.step.{RedisResponse, StepResponse}
import ab.async.tester.library.repository.resource.ResourceRepository
import ab.async.tester.library.substitution.VariableSubstitutionService
import com.google.inject.{Inject, Singleton}
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/**
 * Redis step runner for executing Redis operations
 */
@Singleton
class RedisStepRunner @Inject()(
  resourceRepository: ResourceRepository,
  protected val variableSubstitutionService: VariableSubstitutionService
)(implicit ec: ExecutionContext) extends BaseStepRunner {
  
  override protected val runnerName: String = "RedisStepRunner"
  
  // Cache for Redis connection pools
  private val connectionPools = scala.collection.mutable.Map[String, JedisPool]()

  override def executeStep(step: ExecutionStep, previousResults: List[StepResponse]): Future[StepResponse] = {
    step.meta match {
      case redisMeta: RedisStepMeta =>
        executeRedisOperation(step, redisMeta)
      case _ =>
        Future.successful(createErrorResponse(step, "Invalid step meta type for Redis runner"))
    }
  }

  private def executeRedisOperation(step: ExecutionStep, redisMeta: RedisStepMeta): Future[StepResponse] = {
    logger.info(s"Executing Redis operation ${redisMeta.operation} for step ${step.name}")
    
    resourceRepository.findById(redisMeta.resourceId).flatMap {
      case Some(resource: CacheConfig) =>
        executeOperationWithResource(step, redisMeta, resource)
      case None =>
        Future.successful(createErrorResponse(step, s"Redis resource not found: ${redisMeta.resourceId}"))
    }
  }

  private def executeOperationWithResource(step: ExecutionStep, redisMeta: RedisStepMeta, resource: CacheConfig): Future[StepResponse] = {
    Future {
      try {
        val jedis = getJedisConnection(resource)
        
        try {
          val result = executeOperation(jedis, redisMeta)
          
          // Validate results against expectations
          val validationError = validateResults(redisMeta, result)
          if (validationError.isDefined) {
            createErrorResponse(step, validationError.get)
          } else {
            createSuccessResponse(step, result)
          }
        } finally {
          jedis.close()
        }
        
      } catch {
        case e: Exception =>
          logger.error(s"Redis operation failed for step ${step.name}: ${e.getMessage}", e)
          createErrorResponse(step, s"Redis operation failed: ${e.getMessage}")
      }
    }
  }

  private def getJedisConnection(resource: CacheConfig): Jedis = {
    val resourceId = resource.id
    
    connectionPools.getOrElseUpdate(resourceId, {
      val host = resource.url
      val port = resource.port
      val password = resource.password
      val database = resource.database.getOrElse(0)
      val timeout = 1000 // TODO take it from config
      
      val poolConfig = new JedisPoolConfig()
      poolConfig.setMaxTotal(10)
      poolConfig.setMaxIdle(5)
      poolConfig.setMinIdle(1)
      poolConfig.setTestOnBorrow(true)
      
      new JedisPool(poolConfig, host, port, timeout, password.orNull, database)
    }).getResource
  }

  private def executeOperation(jedis: Jedis, redisMeta: RedisStepMeta): RedisResponse = {
    redisMeta.operation match {
      case RedisOperation.GET =>
        val value = Option(jedis.get(redisMeta.key))
        RedisResponse("GET", redisMeta.key, value = value, exists = Some(value.isDefined))
        
      case RedisOperation.SET =>
        val value = redisMeta.value.getOrElse(throw new IllegalArgumentException("Value is required for SET operation"))
        val result = redisMeta.ttl match {
          case Some(ttlSeconds) if ttlSeconds > 0 => jedis.setex(redisMeta.key, ttlSeconds, value)
          case _ => jedis.set(redisMeta.key, value)
        }
        RedisResponse("SET", redisMeta.key, value = Some(result))
        
      case RedisOperation.DEL =>
        val count = jedis.del(redisMeta.key)
        RedisResponse("DEL", redisMeta.key, count = Some(count.toInt))
        
      case RedisOperation.EXISTS =>
        val exists = jedis.exists(redisMeta.key)
        RedisResponse("EXISTS", redisMeta.key, exists = Some(exists))
        
      case RedisOperation.EXPIRE =>
        val ttl = redisMeta.ttl match {
          case Some(ttlSeconds) if ttlSeconds > 0 => Some(ttlSeconds)
          case _ => None
        }

        ttl.orElse(throw new IllegalArgumentException("TTL is required for EXPIRE operation"))
        val result = jedis.expire(redisMeta.key, ttl.get)
        RedisResponse("EXPIRE", redisMeta.key, count = Some(if (result == 1) 1 else 0))
        
      case RedisOperation.TTL =>
        val ttl = jedis.ttl(redisMeta.key)
        RedisResponse("TTL", redisMeta.key, count = Some(ttl.toInt))
        
      case RedisOperation.HGET =>
        val field = redisMeta.field.getOrElse(throw new IllegalArgumentException("Field is required for HGET operation"))
        val value = Option(jedis.hget(redisMeta.key, field))
        RedisResponse("HGET", redisMeta.key, value = value)
        
      case RedisOperation.HSET =>
        val field = redisMeta.field.getOrElse(throw new IllegalArgumentException("Field is required for HSET operation"))
        val value = redisMeta.value.getOrElse(throw new IllegalArgumentException("Value is required for HSET operation"))
        val result = jedis.hset(redisMeta.key, field, value)
        RedisResponse("HSET", redisMeta.key, count = Some(result.toInt))
        
      case RedisOperation.HGETALL =>
        val hash = jedis.hgetAll(redisMeta.key).asScala.toMap
        RedisResponse("HGETALL", redisMeta.key, values = Some(hash))
        
      case RedisOperation.HDEL =>
        val field = redisMeta.field.getOrElse(throw new IllegalArgumentException("Field is required for HDEL operation"))
        val count = jedis.hdel(redisMeta.key, field)
        RedisResponse("HDEL", redisMeta.key, count = Some(count.toInt))
        
      case RedisOperation.HKEYS =>
        val keys = jedis.hkeys(redisMeta.key).asScala.toList
        val keyMap = keys.zipWithIndex.map { case (key, index) => index.toString -> key }.toMap
        RedisResponse("HKEYS", redisMeta.key, values = Some(keyMap))
      case RedisOperation.LPUSH =>
        val value = redisMeta.value.getOrElse(throw new IllegalArgumentException("Value is required for LPUSH operation"))
        val length = jedis.lpush(redisMeta.key, value)
        RedisResponse("LPUSH", redisMeta.key, count = Some(length.toInt))
        
      case RedisOperation.RPUSH =>
        val value = redisMeta.value.getOrElse(throw new IllegalArgumentException("Value is required for RPUSH operation"))
        val length = jedis.rpush(redisMeta.key, value)
        RedisResponse("RPUSH", redisMeta.key, count = Some(length.toInt))
        
      case RedisOperation.LPOP =>
        val value = Option(jedis.lpop(redisMeta.key))
        RedisResponse("LPOP", redisMeta.key, value = value)
        
      case RedisOperation.RPOP =>
        val value = Option(jedis.rpop(redisMeta.key))
        RedisResponse("RPOP", redisMeta.key, value = value)
        
      case RedisOperation.LLEN =>
        val length = jedis.llen(redisMeta.key)
        RedisResponse("LLEN", redisMeta.key, count = Some(length.toInt))
        
      case RedisOperation.LRANGE =>
        val start = redisMeta.fields.flatMap(_.get("start")).map(_.toInt).getOrElse(0)
        val end = redisMeta.fields.flatMap(_.get("end")).map(_.toInt).getOrElse(-1)
        val values = jedis.lrange(redisMeta.key, start, end).asScala.toList
        val valueMap = values.zipWithIndex.map { case (value, index) => index.toString -> value }.toMap
        RedisResponse("LRANGE", redisMeta.key, values = Some(valueMap))
    }
  }

  private def validateResults(redisMeta: RedisStepMeta, result: RedisResponse): Option[String] = {
    // Validate expected value
    redisMeta.expectedValue.foreach { expectedValue =>
      result.value match {
        case Some(actualValue) if actualValue != expectedValue =>
          return Some(s"Expected value '$expectedValue', but got '$actualValue'")
        case None =>
          return Some(s"Expected value '$expectedValue', but got null")
        case _ => // Value matches
      }
    }
    
    // Validate expected existence
    redisMeta.expectedExists.foreach { expectedExists =>
      result.exists match {
        case Some(actualExists) if actualExists != expectedExists =>
          return Some(s"Expected exists=$expectedExists, but got exists=$actualExists")
        case None =>
          return Some(s"Expected exists=$expectedExists, but exists check was not performed")
        case _ => // Existence matches
      }
    }
    
    None // No validation errors
  }
}
