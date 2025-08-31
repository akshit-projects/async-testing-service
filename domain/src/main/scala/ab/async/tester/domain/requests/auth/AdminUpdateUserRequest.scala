package ab.async.tester.domain.requests.auth

import ab.async.tester.domain.user.UserRole
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class AdminUpdateUserRequest(
  userId: String,
  role: Option[UserRole] = None,
  isAdmin: Option[Boolean] = None
)

object AdminUpdateUserRequest {
  implicit val encoder: Encoder[AdminUpdateUserRequest] = deriveEncoder
  implicit val decoder: Decoder[AdminUpdateUserRequest] = deriveDecoder
}
