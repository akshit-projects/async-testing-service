package ab.async.tester.domain.enums

import ab.async.tester.domain.enums.StepStatus.{ERROR, SUCCESS}
import io.circe.{Decoder, Encoder}

sealed trait StepType
object StepType {
  case object HttpRequest    extends StepType
  case object KafkaPublish   extends StepType
  case object KafkaSubscribe extends StepType
  case object Delay          extends StepType

  implicit val encodeStepStatus: Encoder[StepType] = Encoder.encodeString.contramap[StepType] {
    case HttpRequest => "http_request"
    case KafkaPublish   => "kafka_publish"
    case KafkaSubscribe   => "kafka_subscribe"
    case Delay   => "delay"
  }

  implicit val decodeStepStatus: Decoder[StepType] = Decoder.decodeString.emap {
    case "http_request"     | "http"      => Right(HttpRequest)
    case "kafka_publish"    | "kafka_pub" => Right(KafkaPublish)
    case "kafka_subscribe"  | "kafka_sub" => Right(KafkaSubscribe)
    case "delay"                          => Right(Delay)
    case other                            => Left(s"Unknown StepStatus: $other")
  }
}