package ab.async.tester.domain.testsuite

import ab.async.tester.domain.enums.ExecutionStatus
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.time.Instant

/**
 * Represents the execution of a single flow within a test suite
 * @param flowId The ID of the flow that was executed
 * @param flowVersion The version of the flow that was executed
 * @param executionId The ID of the flow execution
 * @param status The current status of the flow execution
 * @param startedAt When the flow execution started
 * @param completedAt When the flow execution completed (if completed)
 * @param parameters Parameters passed to the flow execution
 */
case class TestSuiteFlowExecution(
  flowId: String,
  flowVersion: Int,
  executionId: String,
  status: ExecutionStatus,
  startedAt: Instant,
  completedAt: Option[Instant] = None,
  parameters: Option[Map[String, String]] = None
)

/**
 * Represents the execution of an entire test suite
 * @param id Unique identifier for the test suite execution
 * @param testSuiteId The ID of the test suite being executed
 * @param testSuiteName Name of the test suite (for convenience)
 * @param status Overall status of the test suite execution
 * @param startedAt When the test suite execution started
 * @param completedAt When the test suite execution completed (if completed)
 * @param flowExecutions List of individual flow executions within this test suite
 * @param runUnordered Whether flows were executed in parallel or sequentially
 * @param triggeredBy User who triggered the test suite execution
 */
case class TestSuiteExecution(
  id: String,
  testSuiteId: String,
  testSuiteName: String,
  status: ExecutionStatus,
  startedAt: Instant,
  completedAt: Option[Instant] = None,
  flowExecutions: List[TestSuiteFlowExecution] = List.empty,
  runUnordered: Boolean = false,
  triggeredBy: String,
)

object TestSuiteFlowExecution {
  implicit val testSuiteFlowExecutionEncoder: Encoder[TestSuiteFlowExecution] = deriveEncoder
  implicit val testSuiteFlowExecutionDecoder: Decoder[TestSuiteFlowExecution] = deriveDecoder
}

object TestSuiteExecution {
  implicit val testSuiteExecutionEncoder: Encoder[TestSuiteExecution] = deriveEncoder
  implicit val testSuiteExecutionDecoder: Decoder[TestSuiteExecution] = deriveDecoder
}
