package ab.async.tester.domain.enums

import ab.async.tester.domain.enums.ConditionOperator.{Contains, Equals, Exists, GreaterThan, LessThan, NotEquals}
import io.circe.{Decoder, Encoder}

sealed trait AlertProvider

object AlertProvider {
  case object OPSGENIE extends AlertProvider

  def fromString(value: String): AlertProvider =
    value match {
      case "opsgenie" => OPSGENIE
      case other      => throw new IllegalArgumentException(s"Unknown AlertProvider: $other")
    }

  implicit val encoder: Encoder[AlertProvider] =
    Encoder.encodeString.contramap[AlertProvider] {
      case OPSGENIE      => "opsgenie"
    }

  implicit val decoder: Decoder[AlertProvider] = Decoder.decodeString.emap {
    case "opsgenie"     => Right(OPSGENIE)
    case other => Left(s"Unknown ConditionOperator: $other")
  }
}
