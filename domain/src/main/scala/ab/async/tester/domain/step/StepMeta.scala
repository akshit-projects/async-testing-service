package ab.async.tester.domain.step

import ab.async.tester.domain.kafka.KafkaSearchPattern
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
    searchPattern: Option[KafkaSearchPattern] = None
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
  implicit val httpStepMetaDecoder: Decoder[HttpStepMeta] =
    deriveDecoder[HttpStepMeta]
  implicit val kafkaSubscribeMetaDecoder: Decoder[KafkaSubscribeMeta] =
    deriveDecoder[KafkaSubscribeMeta]
  implicit val kafkaPublishMetaDecoder: Decoder[KafkaPublishMeta] =
    deriveDecoder[KafkaPublishMeta]
  implicit val delayStepMetaDecoder: Decoder[DelayStepMeta] =
    deriveDecoder[DelayStepMeta]
  implicit val sqlStepMetaDecoder: Decoder[SqlStepMeta] =
    deriveDecoder[SqlStepMeta]
  implicit val redisStepMetaDecoder: Decoder[RedisStepMeta] =
    deriveDecoder[RedisStepMeta]
  implicit val lokiStepMetaDecoder: Decoder[LokiStepMeta] =
    deriveDecoder[LokiStepMeta]

  implicit val httpStepMetaEncoder: Encoder[HttpStepMeta] =
    deriveEncoder[HttpStepMeta]
  implicit val kafkaSubscribeMetaEncoder: Encoder[KafkaSubscribeMeta] =
    deriveEncoder[KafkaSubscribeMeta]
  implicit val kafkaPublishMetaEncoder: Encoder[KafkaPublishMeta] =
    deriveEncoder[KafkaPublishMeta]
  implicit val delayStepMetaEncoder: Encoder[DelayStepMeta] =
    deriveEncoder[DelayStepMeta]
  implicit val sqlStepMetaEncoder: Encoder[SqlStepMeta] =
    deriveEncoder[SqlStepMeta]
  implicit val redisStepMetaEncoder: Encoder[RedisStepMeta] =
    deriveEncoder[RedisStepMeta]
  implicit val lokiStepMetaEncoder: Encoder[LokiStepMeta] =
    deriveEncoder[LokiStepMeta]

  // Trait encoder
  implicit val encodeStepMeta: Encoder[StepMeta] = Encoder.instance {
    case httpMeta @ HttpStepMeta(_, _, _, _, _)              => httpMeta.asJson
    case kafkaSubMeta @ KafkaSubscribeMeta(_, _, _, _, _, _) =>
      kafkaSubMeta.asJson
    case kafkaPubMeta @ KafkaPublishMeta(_, _, _) => kafkaPubMeta.asJson
    case delayMeta @ DelayStepMeta(_)             => delayMeta.asJson
    case sqlMeta @ SqlStepMeta(_, _, _, _, _, _)  => sqlMeta.asJson
    case redisMeta @ RedisStepMeta(_, _, _, _, _, _, _, _, _) =>
      redisMeta.asJson
    case lokiMeta @ LokiStepMeta(_, _, _, _, _, _, _, _, _) => lokiMeta.asJson
  }

  // Type discriminator field for more control
  implicit val decodeStepMeta: Decoder[StepMeta] = (c: HCursor) => {
    if (c.downField("body").succeeded || c.downField("method").succeeded) {
      c.as[HttpStepMeta]
    } else if (c.downField("delayMs").succeeded) {
      c.as[DelayStepMeta]
    } else if (c.downField("groupId").succeeded) {
      c.as[KafkaSubscribeMeta]
    } else if (c.downField("messages").succeeded) {
      c.as[KafkaPublishMeta]
    } else if (c.downField("query").succeeded) {
      c.as[SqlStepMeta]
    } else if (
      c.downField("operation").succeeded && c.downField("key").succeeded
    ) {
      c.as[RedisStepMeta]
    } else if (
      c.downField("namespace").succeeded && c.downField("labels").succeeded
    ) {
      c.as[LokiStepMeta]
    } else {
      Left(DecodingFailure("Could not determine step meta type", c.history))
    }
  }
}
