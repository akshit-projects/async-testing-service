package ab.async.tester.domain.step

import ab.async.tester.domain.enums.StepType
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class FlowStep(
                     id: Option[String] = None,
                     name: String,
                     stepType: StepType,
                     meta: StepMeta,
                     timeoutMs: Int,
                     runInBackground: Boolean = false,
                     continueOnSuccess: Boolean = true
)

object FlowStep {
  // Encoder for FlowStep
  implicit val flowStepEncoder: Encoder[FlowStep] = deriveEncoder[FlowStep]

  // Decoder for FlowStep
  implicit val flowStepDecoder: Decoder[FlowStep] = deriveDecoder[FlowStep]
}