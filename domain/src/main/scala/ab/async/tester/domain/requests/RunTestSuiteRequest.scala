package ab.async.tester.domain.requests

/**
 * Request to trigger execution of a test suite
 * @param testSuiteId The ID of the test suite to execute
 * @param triggeredBy User who is triggering the test suite execution
 * @param globalParameters Optional global parameters to override/merge with individual flow parameters
 */
case class RunTestSuiteRequest(
  testSuiteId: String,
  triggeredBy: String,
  globalParameters: Option[Map[String, String]] = None
)
