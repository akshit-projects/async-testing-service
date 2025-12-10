package ab.async.tester.domain.requests.organisation

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/**
 * Request model for updating an organisation
 * Does not include id (from path), createdAt, modifiedAt (system managed)
 */
case class UpdateOrganisationRequest(
  name: String
)

object UpdateOrganisationRequest {
  implicit val encoder: Encoder[UpdateOrganisationRequest] = deriveEncoder
  implicit val decoder: Decoder[UpdateOrganisationRequest] = deriveDecoder
}

