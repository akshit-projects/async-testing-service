package ab.async.tester.domain.resource

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Loki resource configuration
  */
case class LokiResourceConfig(
    id: String,
    `namespace`: Option[String],
    group: Option[String],
    `type`: String,
    name: String,
    url: String, // e.g., "http://loki:3100"
    authToken: Option[String] = None, // Optional Bearer token
    timeout: Option[Int] = Some(30000)
) extends ResourceConfig {
  override def getId: String = id
  override def getType: String = "loki"
  override def setId(newId: String): ResourceConfig = this.copy(id = newId)
}

object LokiResourceConfig {
  implicit val encoder: Encoder[LokiResourceConfig] =
    deriveEncoder[LokiResourceConfig]
  implicit val decoder: Decoder[LokiResourceConfig] =
    deriveDecoder[LokiResourceConfig]
}
