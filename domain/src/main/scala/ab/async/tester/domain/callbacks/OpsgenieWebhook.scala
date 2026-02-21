package ab.async.tester.domain.callbacks

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/** Opsgenie Webhook Payload Reference:
  * https://support.atlassian.com/opsgenie/docs/opsgenie-webhook-integration-payload/
  */
case class OpsgenieAlert(
    alertId: String,
    message: String,
    createdAt: Long,
    tags: List[String],
    priority: String,
    source: String,
    alias: String,
    entity: Option[String] = None,
    description: Option[String] = None
)

case class OpsgeniePayload(
    action: String,
    alert: OpsgenieAlert,
    integrationId: String,
    integrationName: String,
    source: Map[String, String]
)

object OpsgenieAlert {
  implicit val decoder: Decoder[OpsgenieAlert] = deriveDecoder
  implicit val encoder: Encoder[OpsgenieAlert] = deriveEncoder
}

object OpsgeniePayload {
  implicit val decoder: Decoder[OpsgeniePayload] = deriveDecoder
  implicit val encoder: Encoder[OpsgeniePayload] = deriveEncoder
}
