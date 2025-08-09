package ab.async.tester.domain.step

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}

trait StepMeta

case class HttpStepMeta(
 resourceId: String,
 body: Option[String] = None,
 headers: Option[Map[String, String]] = None,
 expectedStatus: Option[String] = None,
 expectedResponse: Option[String] = None
) extends StepMeta

case class KafkaSubscribeMeta(
 resourceId: String,
 topicName: String,
 groupId: String,
 maxMessages: Int,
 fromBeginning: Boolean,
) extends StepMeta

case class KafkaPublishMeta(
  resourceId: String,
  topicName: String,
  messages: List[KafkaMessage]
) extends StepMeta


case class DelayStepMeta(
 delayMs: Long
) extends StepMeta

object StepMeta {
  // Individual encoders and decoders
  implicit val httpStepMetaDecoder: Decoder[HttpStepMeta] = deriveDecoder[HttpStepMeta]
  implicit val kafkaSubscribeMetaDecoder: Decoder[KafkaSubscribeMeta] = deriveDecoder[KafkaSubscribeMeta]
  implicit val kafkaPublishMetaDecoder: Decoder[KafkaPublishMeta] = deriveDecoder[KafkaPublishMeta]
  implicit val delayStepMetaDecoder: Decoder[DelayStepMeta] = deriveDecoder[DelayStepMeta]

  implicit val httpStepMetaEncoder: Encoder[HttpStepMeta] = deriveEncoder[HttpStepMeta]
  implicit val kafkaSubscribeMetaEncoder: Encoder[KafkaSubscribeMeta] = deriveEncoder[KafkaSubscribeMeta]
  implicit val kafkaPublishMetaEncoder: Encoder[KafkaPublishMeta] = deriveEncoder[KafkaPublishMeta]
  implicit val delayStepMetaEncoder: Encoder[DelayStepMeta] = deriveEncoder[DelayStepMeta]

  // Trait encoder
  implicit val encodeStepMeta: Encoder[StepMeta] = Encoder.instance {
    case httpMeta @ HttpStepMeta(_, _, _, _, _) => httpMeta.asJson
    case kafkaSubMeta @ KafkaSubscribeMeta(_, _, _, _, _) => kafkaSubMeta.asJson
    case kafkaPubMeta @ KafkaPublishMeta(_, _, _) => kafkaPubMeta.asJson
    case delayMeta @ DelayStepMeta(_) => delayMeta.asJson
  }

  // Type discriminator field for more control
  implicit val decodeStepMeta: Decoder[StepMeta] = new Decoder[StepMeta] {
    def apply(c: HCursor): Decoder.Result[StepMeta] = {
      if (c.downField("expectedStatus").succeeded) {
        c.as[HttpStepMeta]
      } else if (c.downField("delayMs").succeeded) {
        c.as[DelayStepMeta]
      } else if (c.downField("groupId").succeeded && c.downField("maxMessages").succeeded) {
        c.as[KafkaSubscribeMeta]
      } else if (c.downField("messages").succeeded) {
        c.as[KafkaPublishMeta]
      } else {
        Left(DecodingFailure("Could not determine step meta type", c.history))
      }
    }
  }
}
