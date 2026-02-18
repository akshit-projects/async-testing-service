package ab.async.tester.domain.step

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class KafkaMessage(
  key: Option[String],
  value: String,
  partition: Option[Int] = None,
  offset: Option[Long] = None,
  timestamp: Option[Long] = None
)

object KafkaMessage {
  implicit val kafkaMessageEncoder: Encoder[KafkaMessage] = deriveEncoder[KafkaMessage]
  implicit val kafkaMessageDecoder: Decoder[KafkaMessage] = deriveDecoder[KafkaMessage]

}