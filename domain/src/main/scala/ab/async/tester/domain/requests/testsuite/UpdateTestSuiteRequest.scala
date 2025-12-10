package ab.async.tester.domain.requests.testsuite

import ab.async.tester.domain.testsuite.TestSuiteFlowConfig
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/**
 * Request model for updating a test suite
 * Does not include id (from path), createdAt, modifiedAt (system managed)
 */
case class UpdateTestSuiteRequest(
  name: String,
  description: Option[String] = None,
  flows: List[TestSuiteFlowConfig],
  runUnordered: Boolean = false,
  enabled: Boolean = true,
  orgId: Option[String] = None,
  teamId: Option[String] = None
)

object UpdateTestSuiteRequest {
  implicit val encoder: Encoder[UpdateTestSuiteRequest] = deriveEncoder
  implicit val decoder: Decoder[UpdateTestSuiteRequest] = deriveDecoder
}

