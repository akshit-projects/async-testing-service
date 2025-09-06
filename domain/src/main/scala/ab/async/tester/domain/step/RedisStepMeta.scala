package ab.async.tester.domain.step

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
 * Meta information for Redis operations
 */
case class RedisStepMeta(
  resourceId: String,
  operation: RedisOperation,
  key: String,
  value: Option[String] = None,
  field: Option[String] = None,
  fields: Option[Map[String, String]] = None,
  ttl: Option[Int] = None,
  expectedValue: Option[String] = None,
  expectedExists: Option[Boolean] = None
) extends StepMeta

/**
 * Supported Redis operations
 */
sealed trait RedisOperation

object RedisOperation {
  case object GET extends RedisOperation
  case object SET extends RedisOperation
  case object DEL extends RedisOperation
  case object EXISTS extends RedisOperation
  case object EXPIRE extends RedisOperation
  case object TTL extends RedisOperation
  case object HGET extends RedisOperation
  case object HSET extends RedisOperation
  case object HGETALL extends RedisOperation
  case object HDEL extends RedisOperation
  case object HKEYS extends RedisOperation
  case object LPUSH extends RedisOperation
  case object RPUSH extends RedisOperation
  case object LPOP extends RedisOperation
  case object RPOP extends RedisOperation
  case object LLEN extends RedisOperation
  case object LRANGE extends RedisOperation

  implicit val redisOperationEncoder: Encoder[RedisOperation] = Encoder.encodeString.contramap {
    case GET => "get"
    case SET => "set"
    case DEL => "del"
    case EXISTS => "exists"
    case EXPIRE => "expire"
    case TTL => "ttl"
    case HGET => "hget"
    case HSET => "hset"
    case HGETALL => "hgetall"
    case HDEL => "hdel"
    case HKEYS => "hkeys"
    case LPUSH => "lpush"
    case RPUSH => "rpush"
    case LPOP => "lpop"
    case RPOP => "rpop"
    case LLEN => "llen"
    case LRANGE => "lrange"
  }

  implicit val redisOperationDecoder: Decoder[RedisOperation] = Decoder.decodeString.emap {
    case "get" => Right(GET)
    case "set" => Right(SET)
    case "del" => Right(DEL)
    case "exists" => Right(EXISTS)
    case "expire" => Right(EXPIRE)
    case "ttl" => Right(TTL)
    case "hget" => Right(HGET)
    case "hset" => Right(HSET)
    case "hgetall" => Right(HGETALL)
    case "hdel" => Right(HDEL)
    case "hkeys" => Right(HKEYS)
    case "lpush" => Right(LPUSH)
    case "rpush" => Right(RPUSH)
    case "lpop" => Right(LPOP)
    case "rpop" => Right(RPOP)
    case "llen" => Right(LLEN)
    case "lrange" => Right(LRANGE)
    case other => Left(s"Unknown Redis operation: $other")
  }
}

object RedisStepMeta {
  implicit val redisStepMetaEncoder: Encoder[RedisStepMeta] = deriveEncoder
  implicit val redisStepMetaDecoder: Decoder[RedisStepMeta] = deriveDecoder
}
