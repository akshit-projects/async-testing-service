package ab.async.tester.domain.execution

import ab.async.tester.domain.enums.ExecutionStatus
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.time.Instant
/**
 * Represents a flow execution record
 */
case class Execution(
  id: String,
  flowId: String,
  flowVersion: Int,
  status: ExecutionStatus,
  startedAt: Instant,
  completedAt: Option[Instant] = None,
  steps: List[ExecutionStep],
  updatedAt: Instant,
  parameters: Option[Map[String, String]] = None,
  testSuiteExecutionId: Option[String] = None
)


case class StepLog(
  timestamp: Instant,
  message: String,
)


object Execution {
  implicit val encoder: Encoder[Execution] = deriveEncoder
  implicit val decoder: Decoder[Execution] = deriveDecoder
}

object StepLog {
  implicit val encoder: Encoder[StepLog] = deriveEncoder
  implicit val decoder: Decoder[StepLog] = deriveDecoder
}