package ab.async.tester.domain.enums

import ab.async.tester.domain.step.StepTypeRegistry
import io.circe.{Decoder, Encoder}

sealed trait StepType {
  def stringified: String =
    StepTypeRegistry.getMetadata(this).identifier
}

object StepType {

  case object HttpRequest extends StepType
  case object KafkaPublish extends StepType
  case object KafkaSubscribe extends StepType
  case object Delay extends StepType
  case object SqlQuery extends StepType
  case object RedisOperation extends StepType
  case object LokiLogSearch extends StepType
  case object Condition extends StepType

  /** Encode using registry identifier */
  implicit val encoder: Encoder[StepType] =
    Encoder.encodeString.contramap(_.stringified)

  /** Decode using registry identifier + aliases */
  implicit val decoder: Decoder[StepType] =
    Decoder.decodeString.emap { raw =>
      StepTypeRegistry
        .fromIdentifier(raw)
        .toRight(s"Unknown StepType: $raw")
    }
}