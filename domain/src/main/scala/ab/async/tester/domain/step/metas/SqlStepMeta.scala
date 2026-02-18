package ab.async.tester.domain.step.metas

import ab.async.tester.domain.enums.StepType
import ab.async.tester.domain.step.StepMeta

/** Meta information for SQL query steps
  */
case class SqlStepMeta(
    resourceId: String,
    query: String,
    parameters: Option[Map[String, String]] = None,
    expectedRowCount: Option[Int] = None,
    expectedColumns: Option[List[String]] = None,
    timeout: Option[Int] = None
) extends StepMeta {
  override def stepType: StepType = StepType.SqlQuery
}
