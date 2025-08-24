package ab.async.tester.domain.resource

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class KafkaResourceConfig(id: String,
                               `namespace`: Option[String],
                               group: Option[String],
                               `type`: String,
                               name: String,
                               brokersList: String,
                               config: Option[Map[String, String]]) extends ResourceConfig {
  override def getId: String = id

  override def getType: String = "kafka"

  override def setId(newId: String): ResourceConfig = this.copy(id = newId)
}

object KafkaResourceConfig {
  implicit val kafkaConfigEncoder: Encoder[KafkaResourceConfig] = deriveEncoder
  implicit val kafkaConfigDecoder: Decoder[KafkaResourceConfig] = deriveDecoder
}
