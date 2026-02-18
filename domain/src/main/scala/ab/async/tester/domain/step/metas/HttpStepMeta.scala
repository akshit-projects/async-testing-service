package ab.async.tester.domain.step.metas

import ab.async.tester.domain.enums.StepType
import ab.async.tester.domain.step.StepMeta

case class HttpStepMeta(
    resourceId: String,
    body: Option[String] = None,
    headers: Option[Map[String, String]] = None,
    expectedStatus: Option[String] = None,
    expectedResponse: Option[String] = None
) extends StepMeta {

  override def stepType: StepType = StepType.HttpRequest
}
