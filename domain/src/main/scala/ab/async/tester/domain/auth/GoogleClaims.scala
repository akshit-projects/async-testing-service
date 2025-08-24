package ab.async.tester.domain.auth

case class GoogleClaims(
                         iss: String,
                         aud: String,
                         exp: Long,
                         email: String,
                         name: Option[String],
                         picture: Option[String]
                       )
