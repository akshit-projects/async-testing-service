package ab.async.tester.domain.response.auth


case class AuthResponse(
                         token: String,
                         refreshToken: String,
                         expiresAt: Long,
                         user: UserInfo
                       )

case class UserInfo(
                     id: String,
                     email: String,
                     name: Option[String],
                     role: String,
                     isAdmin: Boolean,
                     orgIds: Option[List[String]] = None,
                     teamIds: Option[List[String]] = None
                   )