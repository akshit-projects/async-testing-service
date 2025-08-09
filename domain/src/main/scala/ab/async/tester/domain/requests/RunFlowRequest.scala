package ab.async.tester.domain.requests

case class RunFlowRequest(
                         flowId: String,
                         params: Map[String, String] = Map.empty
                         )
