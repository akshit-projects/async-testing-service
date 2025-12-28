package ab.async.tester.domain.enums

import io.circe.{Decoder, Encoder}

sealed trait StepType {
  def stringified: String = this match {
    case StepType.HttpRequest    => "http"
    case StepType.KafkaPublish   => "kafka_publish"
    case StepType.KafkaSubscribe => "kafka_subscribe"
    case StepType.Delay          => "delay"
    case StepType.SqlQuery       => "sql-db"
    case StepType.RedisOperation => "cache"
    case StepType.LokiLogSearch  => "loki_logs"
  }
}
object StepType {
  case object HttpRequest extends StepType
  case object KafkaPublish extends StepType
  case object KafkaSubscribe extends StepType
  case object Delay extends StepType
  case object SqlQuery extends StepType
  case object RedisOperation extends StepType
  case object LokiLogSearch extends StepType

  implicit val encodeStepStatus: Encoder[StepType] =
    Encoder.encodeString.contramap[StepType] {
      case HttpRequest    => "http"
      case KafkaPublish   => "kafka_publish"
      case KafkaSubscribe => "kafka_subscribe"
      case Delay          => "delay"
      case SqlQuery       => "sql-db"
      case RedisOperation => "cache"
      case LokiLogSearch  => "loki_logs"
    }

  implicit val decodeStepStatus: Decoder[StepType] = Decoder.decodeString.emap {
    case "http"                          => Right(HttpRequest)
    case "kafka_publish" | "kafka_pub"   => Right(KafkaPublish)
    case "kafka_subscribe" | "kafka_sub" => Right(KafkaSubscribe)
    case "delay"                         => Right(Delay)
    case "sql-db"                        => Right(SqlQuery)
    case "cache"                         => Right(RedisOperation)
    case "loki_logs" | "loki"            => Right(LokiLogSearch)
    case other                           => Left(s"Unknown StepStatus: $other")
  }
}
