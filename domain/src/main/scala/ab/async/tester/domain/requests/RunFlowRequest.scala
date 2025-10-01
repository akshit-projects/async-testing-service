package ab.async.tester.domain.requests

import ab.async.tester.domain.variable.VariableValue

case class RunFlowRequest(
  flowId: String,
  testSuiteExecutionId: Option[String] = None,
  params: Map[String, String] = Map.empty,
  variables: List[VariableValue]
)
