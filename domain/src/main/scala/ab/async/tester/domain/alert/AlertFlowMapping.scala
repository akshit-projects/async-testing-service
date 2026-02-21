package ab.async.tester.domain.alert

import ab.async.tester.domain.alert.triggermetas.OpsgenieAlertTriggerMeta
import ab.async.tester.domain.enums.AlertProvider
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, DecodingFailure, Encoder}

case class AlertFlowMapping(
   id: Option[String] = None,
   provider: AlertProvider,
   triggerMeta: AlertTriggerMeta,
   flowId: String,
   reportingConfig: Option[ReportingConfig],
   orgId: Option[String],
   teamId: Option[String],
   isActive: Boolean = true,
   createdAt: Long = System.currentTimeMillis() / 1000,
   updatedAt: Long = System.currentTimeMillis() / 1000
)

trait AlertTriggerMeta {
  val `type`: String
}
object AlertTriggerMeta {

  implicit val encodeAlertTriggerMeta: Encoder[AlertTriggerMeta] =
    Encoder.instance {
      case o: OpsgenieAlertTriggerMeta => o.asJson
    }

  implicit val decodeAlertTriggerMeta: Decoder[AlertTriggerMeta] =
    Decoder.instance { c =>
      c.downField("type").as[String].flatMap {

        case "opsgenie" =>
          c.downField("alert").as[OpsgenieAlertTriggerMeta]

        case other =>
          Left(DecodingFailure(s"Unknown AlertTriggerMeta type: $other", c.history))
      }
    }
}



object AlertFlowMapping {
  implicit val decoder: Decoder[AlertFlowMapping] = deriveDecoder
  implicit val encoder: Encoder[AlertFlowMapping] = deriveEncoder
}
