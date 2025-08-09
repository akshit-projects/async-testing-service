package ab.async.tester.domain.execution

import ab.async.tester.domain.enums.{StepStatus, StepType}
import ab.async.tester.domain.step.{FlowStep, StepMeta, StepResponseValue}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.time.Instant

case class ExecutionStep(
  id: Option[String],
  name: String,
  stepType: StepType,
  meta: Option[StepMeta],
  timeoutMs: Int,
  runInBackground: Boolean = false,
  continueOnSuccess: Boolean = true,
  status: StepStatus,
  startedAt: Instant,
  completedAt: Option[Instant] = None,
  logs: Seq[StepLog] = Seq.empty,
  response: Option[StepResponseValue] = None,
)


object ExecutionStep {
  implicit val encoder: Encoder[ExecutionStep] = deriveEncoder
  implicit val decoder: Decoder[ExecutionStep] = deriveDecoder
}