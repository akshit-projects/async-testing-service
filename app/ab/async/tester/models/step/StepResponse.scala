package ab.async.tester.models.step

import ab.async.tester.models.enums.StepStatus
import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto._

case class StepResponse(
 name: String,
 status: StepStatus,
 response: StepResponseValue,
 id: String
)

object StepResponse {
  implicit val kafkaMessageEncoder: Encoder[StepResponse] = deriveEncoder[StepResponse]
  implicit val kafkaMessageDecoder: Decoder[StepResponse] = deriveDecoder[StepResponse]
}
