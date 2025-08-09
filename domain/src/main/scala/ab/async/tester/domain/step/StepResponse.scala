package ab.async.tester.domain.step

import ab.async.tester.domain.enums.StepStatus
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

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
