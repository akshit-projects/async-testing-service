package ab.async.tester.domain.auth

case class GoogleClaims(
                         iss: String,
                         aud: String,
                         sub: String,
                         exp: Long,
                         email: String,
                         name: Option[String],
                         picture: Option[String],
                         email_verified: Option[Boolean]
                       )
