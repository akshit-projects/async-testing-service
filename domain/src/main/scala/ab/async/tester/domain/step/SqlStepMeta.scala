package ab.async.tester.domain.step

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
 * Meta information for SQL query steps
 */
case class SqlStepMeta(
  resourceId: String,
  query: String,
  parameters: Option[Map[String, String]] = None,
  expectedRowCount: Option[Int] = None,
  expectedColumns: Option[List[String]] = None,
  timeout: Option[Int] = None
) extends StepMeta

object SqlStepMeta {
  implicit val sqlStepMetaEncoder: Encoder[SqlStepMeta] = deriveEncoder
  implicit val sqlStepMetaDecoder: Decoder[SqlStepMeta] = deriveDecoder
}
