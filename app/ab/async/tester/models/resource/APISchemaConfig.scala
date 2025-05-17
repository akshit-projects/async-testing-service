package ab.async.tester.models.resource

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

case class APISchemaConfig(id: String,
                           `namespace`: String,
                           group: String,
                           name: String,
                           url: String,
                           method: String,
                           headers: Option[Map[String, String]],
                           requestBody: Option[String],
                           queryParams: Option[Map[String, String]])
  extends ResourceConfig {
  override def getId: String = id

  override def getType: String = "api-schema"

  override def setId(newId: String): ResourceConfig = this.copy(id = newId)
}

object APISchemaConfig {
  implicit val apiSchemaConfigEncoder: Encoder[APISchemaConfig] = deriveEncoder
  implicit val apiSchemaConfigDecoder: Decoder[APISchemaConfig] = deriveDecoder
}

