package ab.async.tester.domain.user

case class User(
               id: String,
               email: String,
               name: Option[String],
               profilePicture: Option[String],
               createdAt: Long,
               modifiedAt: Long,
               )
