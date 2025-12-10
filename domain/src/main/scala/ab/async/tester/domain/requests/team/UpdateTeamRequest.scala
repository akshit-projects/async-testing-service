package ab.async.tester.domain.requests.team

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/**
 * Request model for updating a team
 * Does not include id (from path), createdAt, modifiedAt (system managed)
 */
case class UpdateTeamRequest(
  orgId: String,
  name: String
)

object UpdateTeamRequest {
  implicit val encoder: Encoder[UpdateTeamRequest] = deriveEncoder
  implicit val decoder: Decoder[UpdateTeamRequest] = deriveDecoder
}

