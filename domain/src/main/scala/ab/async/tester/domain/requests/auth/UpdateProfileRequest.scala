package ab.async.tester.domain.requests.auth

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class UpdateProfileRequest(
  name: Option[String] = None,
  phoneNumber: Option[String] = None,
  company: Option[String] = None,
  bio: Option[String] = None
)

object UpdateProfileRequest {
  implicit val encoder: Encoder[UpdateProfileRequest] = deriveEncoder
  implicit val decoder: Decoder[UpdateProfileRequest] = deriveDecoder
}
