package ab.async.tester.domain.execution

import ab.async.tester.domain.enums.{ExecutionStatus, ExecutionUpdateType, StepStatus}
import ab.async.tester.domain.step.StepResponseValue

case class ExecutionStatusUpdate(
                                executionId: String,
                                updateType: ExecutionUpdateType,
                                stepUpdate: Option[StepUpdate] = None,
                                message: Option[String] = None,
                                executionStatus: ExecutionStatus
)

case class StepUpdate(
  stepId: String,
  status: StepStatus,
  response: Option[StepResponseValue] = None,
)