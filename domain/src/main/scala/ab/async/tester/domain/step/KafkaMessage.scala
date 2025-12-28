package ab.async.tester.domain.step

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class KafkaMessage(
  key: Option[String],
  value: String,
  partition: Option[Int],
  offset: Option[Long],
  timestamp: Option[Long]
)

object KafkaMessage {
  implicit val kafkaMessageEncoder: Encoder[KafkaMessage] = deriveEncoder[KafkaMessage]
  implicit val kafkaMessageDecoder: Decoder[KafkaMessage] = deriveDecoder[KafkaMessage]

}