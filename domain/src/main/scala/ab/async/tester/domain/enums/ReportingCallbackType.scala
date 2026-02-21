package ab.async.tester.domain.enums

import ab.async.tester.domain.enums.AlertProvider.OPSGENIE
import io.circe.{Decoder, Encoder}


sealed trait ReportingCallbackType

object ReportingCallbackType {
  case object SLACK extends ReportingCallbackType

  implicit val encoder: Encoder[ReportingCallbackType] =
    Encoder.encodeString.contramap[ReportingCallbackType] {
      case SLACK      => "slack"
    }

  implicit val decoder: Decoder[ReportingCallbackType] = Decoder.decodeString.emap {
    case "slack"     => Right(SLACK)
    case other => Left(s"Unknown ConditionOperator: $other")
  }

}
