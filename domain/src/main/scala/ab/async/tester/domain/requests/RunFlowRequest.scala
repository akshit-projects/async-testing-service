package ab.async.tester.domain.requests

case class RunFlowRequest(
  flowId: String,
  testSuiteExecutionId: Option[String] = None,
  params: Map[String, String] = Map.empty
)
