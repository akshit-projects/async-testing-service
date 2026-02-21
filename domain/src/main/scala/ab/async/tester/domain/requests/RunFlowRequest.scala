package ab.async.tester.domain.requests

import ab.async.tester.domain.alert.ReportingConfig
import ab.async.tester.domain.variable.VariableValue
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class RunFlowRequest(
    flowId: String,
    testSuiteExecutionId: Option[String] = None,
    params: Map[String, String] = Map.empty,
    variables: List[VariableValue],
    reportingConfig: Option[ReportingConfig] = None
)
