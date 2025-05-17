package ab.async.tester.models.step

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class KafkaMessage(
  key: Option[String],
  value: String
)

object KafkaMessage {
  implicit val kafkaMessageEncoder: Encoder[KafkaMessage] = deriveEncoder[KafkaMessage]
  implicit val kafkaMessageDecoder: Decoder[KafkaMessage] = deriveDecoder[KafkaMessage]

}