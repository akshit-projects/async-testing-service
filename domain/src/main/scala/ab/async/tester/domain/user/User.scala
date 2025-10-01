package ab.async.tester.domain.user

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class User(
                 id: String,
                 email: String,
                 name: Option[String],
                 profilePicture: Option[String],
                 phoneNumber: Option[String] = None,
                 company: Option[String] = None,
                 role: UserRole = UserRole.User,
                 bio: Option[String] = None,
                 isAdmin: Boolean = false,
                 orgIds: Option[List[String]] = None,
                 teamIds: Option[List[String]] = None,
                 userUpdatedFields: Set[String] = Set.empty, // Fields that user has manually updated
                 lastGoogleSync: Option[Long] = None, // Last time Google data was synced
                 createdAt: Long,
                 modifiedAt: Long,
               ) {

  // Helper method to check if a field was updated by user
  def isFieldUpdatedByUser(fieldName: String): Boolean = userUpdatedFields.contains(fieldName)

  // Helper method to mark a field as updated by user
  def markFieldAsUpdatedByUser(fieldName: String): User =
    this.copy(userUpdatedFields = userUpdatedFields + fieldName, modifiedAt = System.currentTimeMillis())

}

object User {
  implicit val encoder: Encoder[User] = deriveEncoder
  implicit val decoder: Decoder[User] = deriveDecoder
}
