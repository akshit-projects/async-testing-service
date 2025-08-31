package ab.async.tester.domain.response.auth

import ab.async.tester.domain.user.{User, UserRole}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class UserProfileResponse(
  id: String,
  email: String,
  name: Option[String],
  displayName: String,
  profilePicture: Option[String],
  phoneNumber: Option[String],
  company: Option[String],
  bio: Option[String],
  role: UserRole,
  isAdmin: Boolean,
  createdAt: Long,
  modifiedAt: Long
)

object UserProfileResponse {
  def fromUser(user: User): UserProfileResponse = UserProfileResponse(
    id = user.id,
    email = user.email,
    name = user.name,
    displayName = user.name.getOrElse("User"),
    profilePicture = user.profilePicture,
    phoneNumber = user.phoneNumber,
    company = user.company,
    role = user.role,
    isAdmin = user.isAdmin,
    createdAt = user.createdAt,
    modifiedAt = user.modifiedAt,
    bio = user.bio
  )
  
  implicit val encoder: Encoder[UserProfileResponse] = deriveEncoder
  implicit val decoder: Decoder[UserProfileResponse] = deriveDecoder
}
