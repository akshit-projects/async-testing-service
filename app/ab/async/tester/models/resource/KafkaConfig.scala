package ab.async.tester.models.resource

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

case class KafkaConfig(id: String,
                       `namespace`: String,
                       group: String,
                       name: String,
                       brokerList: String,
                       config: Option[Map[String, String]]) extends ResourceConfig {
  override def getId: String = id

  override def getType: String = "kafka"

  override def setId(newId: String): ResourceConfig = this.copy(id = newId)
}

object KafkaConfig {
  implicit val kafkaConfigEncoder: Encoder[KafkaConfig] = deriveEncoder
  implicit val kafkaConfigDecoder: Decoder[KafkaConfig] = deriveDecoder
}
