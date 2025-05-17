package ab.async.tester.models.step

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

case class StepError(
                    error: String,
                    expectedValue: Option[String],
                    actualValue: Option[String]
                    ) extends StepResponseValue

object StepResponseValue {

  implicit val httpResponseEncoder: Encoder[HttpResponse] = deriveEncoder[HttpResponse]
  implicit val kafkaMessagesResponseEncoder: Encoder[KafkaMessagesResponse] = deriveEncoder[KafkaMessagesResponse]
  implicit val stepErrorEncoder: Encoder[StepError] = deriveEncoder[StepError]

  implicit val encodeStepResponseValue: Encoder[StepResponseValue] = Encoder.instance {
    case httpResponse @ HttpResponse(_, _, _) => httpResponse.asJson
    case kafkaMessagesResponse @ KafkaMessagesResponse(_) => kafkaMessagesResponse.asJson
    case stepError @ StepError(_, _, _) => stepError.asJson
  }

  implicit val httpResponseDecoder: Decoder[HttpResponse] = deriveDecoder[HttpResponse]
  implicit val kafkaMessagesResponseDecoder: Decoder[KafkaMessagesResponse] = deriveDecoder[KafkaMessagesResponse]
  implicit val stepErrorDecoder: Decoder[StepError] = deriveDecoder[StepError]

  implicit val decodeStepResponseValue: Decoder[StepResponseValue] =
    List[Decoder[StepResponseValue]](
      Decoder[HttpResponse].widen,
      Decoder[KafkaMessagesResponse].widen,
      Decoder[StepError].widen,
    ).reduceLeft(_ or _)
}