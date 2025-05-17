package ab.async.tester.models.step

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.generic.auto._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class FlowStep(
  name: String,
  function: String,
  stepType: Option[String] = None,
  meta: StepMeta = null,
  value: Option[String] = None,
  id: Option[String] = None,
  timeout: Int,
  runInBackground: Boolean = false,
  continueOnSuccess: Boolean = true
)

object FlowStep {
  // Encoder for FlowStep
  implicit val flowStepEncoder: Encoder[FlowStep] = deriveEncoder[FlowStep]

  // Decoder for FlowStep
  implicit val flowStepDecoder: Decoder[FlowStep] = deriveDecoder[FlowStep]
}