package ab.async.tester.domain.testsuite

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
 * Configuration for a flow within a test suite
 * @param flowId The ID of the flow to execute
 * @param version The version of the flow to execute (None for latest version, or specific version number as string)
 * @param parameters Optional parameters to pass to the flow execution
 */
case class TestSuiteFlowConfig(
  flowId: String,
  version: Option[Int] = None,
  parameters: Option[Map[String, String]] = None
)

/**
 * Represents a test suite containing multiple flows to be executed
 * @param id Unique identifier for the test suite
 * @param name Human-readable name for the test suite
 * @param description Optional description of the test suite
 * @param creator User who created the test suite
 * @param flows List of flow configurations to execute in this test suite
 * @param runUnordered Whether flows should be executed in parallel (true) or sequentially (false)
 * @param createdAt Timestamp when the test suite was created
 * @param modifiedAt Timestamp when the test suite was last modified
 * @param enabled Whether the test suite is enabled for execution
 */
case class TestSuite(
  id: Option[String] = None,
  name: String,
  description: Option[String] = None,
  creator: String,
  flows: List[TestSuiteFlowConfig],
  runUnordered: Boolean = false,
  createdAt: Long = System.currentTimeMillis(),
  modifiedAt: Long = System.currentTimeMillis(),
  enabled: Boolean = true
)

object TestSuiteFlowConfig {
  implicit val testSuiteFlowConfigEncoder: Encoder[TestSuiteFlowConfig] = deriveEncoder
  implicit val testSuiteFlowConfigDecoder: Decoder[TestSuiteFlowConfig] = deriveDecoder
}

object TestSuite {
  implicit val testSuiteEncoder: Encoder[TestSuite] = deriveEncoder
  implicit val testSuiteDecoder: Decoder[TestSuite] = deriveDecoder
}
