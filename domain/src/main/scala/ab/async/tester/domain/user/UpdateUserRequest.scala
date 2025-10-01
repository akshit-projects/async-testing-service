package ab.async.tester.domain.user

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
 * Request to update user profile
 */
case class UpdateUserRequest(
  name: Option[String] = None,
  phoneNumber: Option[String] = None,
  orgIds: Option[List[String]] = None,
  teamIds: Option[List[String]] = None,
  role: Option[String] = None,
  isAdmin: Option[Boolean] = None,
  isActive: Option[Boolean] = None
)

object UpdateUserRequest {
  implicit val updateUserRequestEncoder: Encoder[UpdateUserRequest] = deriveEncoder
  implicit val updateUserRequestDecoder: Decoder[UpdateUserRequest] = deriveDecoder
}
