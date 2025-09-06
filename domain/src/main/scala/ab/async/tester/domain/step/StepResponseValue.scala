package ab.async.tester.domain.step

import cats.implicits.toFunctorOps
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps

trait StepResponseValue

case class HttpResponse(
 status: Int,
 response: String,
 headers: Map[String, String] = Map.empty
) extends StepResponseValue

case class KafkaMessagesResponse(
  messages: List[KafkaMessage]
) extends StepResponseValue

case class DelayResponse(
  success: Boolean
) extends StepResponseValue

case class SqlResponse(
  rows: List[Map[String, String]],
  rowCount: Int,
  columns: List[String],
  executionTimeMs: Long
) extends StepResponseValue

case class RedisResponse(
  operation: String,
  key: String,
  value: Option[String] = None,
  values: Option[Map[String, String]] = None,
  exists: Option[Boolean] = None,
  count: Option[Int] = None
) extends StepResponseValue

case class StepError(
                    error: String,
                    expectedValue: Option[String],
                    actualValue: Option[String]
                    ) extends StepResponseValue

object StepResponseValue {

  implicit val httpResponseEncoder: Encoder[HttpResponse] = deriveEncoder[HttpResponse]
  implicit val kafkaMessagesResponseEncoder: Encoder[KafkaMessagesResponse] = deriveEncoder[KafkaMessagesResponse]
  implicit val delayResponseEncoder: Encoder[DelayResponse] = deriveEncoder[DelayResponse]
  implicit val sqlResponseEncoder: Encoder[SqlResponse] = deriveEncoder[SqlResponse]
  implicit val redisResponseEncoder: Encoder[RedisResponse] = deriveEncoder[RedisResponse]
  implicit val stepErrorEncoder: Encoder[StepError] = deriveEncoder[StepError]

  implicit val encodeStepResponseValue: Encoder[StepResponseValue] = Encoder.instance {
    case httpResponse @ HttpResponse(_, _, _) => httpResponse.asJson
    case kafkaMessagesResponse @ KafkaMessagesResponse(_) => kafkaMessagesResponse.asJson
    case delayResponse @ DelayResponse(_) => delayResponse.asJson
    case sqlResponse @ SqlResponse(_, _, _, _) => sqlResponse.asJson
    case redisResponse @ RedisResponse(_, _, _, _, _, _) => redisResponse.asJson
    case stepError @ StepError(_, _, _) => stepError.asJson
  }

  implicit val httpResponseDecoder: Decoder[HttpResponse] = deriveDecoder[HttpResponse]
  implicit val kafkaMessagesResponseDecoder: Decoder[KafkaMessagesResponse] = deriveDecoder[KafkaMessagesResponse]
  implicit val delayResponseDecoder: Decoder[DelayResponse] = deriveDecoder[DelayResponse]
  implicit val sqlResponseDecoder: Decoder[SqlResponse] = deriveDecoder[SqlResponse]
  implicit val redisResponseDecoder: Decoder[RedisResponse] = deriveDecoder[RedisResponse]
  implicit val stepErrorDecoder: Decoder[StepError] = deriveDecoder[StepError]

  implicit val decodeStepResponseValue: Decoder[StepResponseValue] =
    List[Decoder[StepResponseValue]](
      Decoder[HttpResponse].widen,
      Decoder[KafkaMessagesResponse].widen,
      Decoder[DelayResponse].widen,
      Decoder[SqlResponse].widen,
      Decoder[RedisResponse].widen,
      Decoder[StepError].widen,
    ).reduceLeft(_ or _)
}