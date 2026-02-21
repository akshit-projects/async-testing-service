package ab.async.tester.domain.alert.triggermetas
import ab.async.tester.domain.alert.AlertTriggerMeta
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class OpsgenieAlertTriggerMeta(
      `type`: String,
                                     alertId: String
                                   ) extends AlertTriggerMeta {
}

object OpsgenieAlertTriggerMeta {
  implicit val decoder: Decoder[OpsgenieAlertTriggerMeta] = deriveDecoder
  implicit val encoder: Encoder[OpsgenieAlertTriggerMeta] = deriveEncoder
}