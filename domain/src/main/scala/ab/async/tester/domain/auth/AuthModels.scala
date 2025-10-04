package ab.async.tester.domain.auth

case class EmailLoginRequest(
  email: String,
  password: String
)

case class RegisterRequest(
  email: String,
  password: String,
  name: Option[String] = None
)

case class ForgotPasswordRequest(
  email: String
)

case class ResetPasswordRequest(
  token: String,
  newPassword: String
)

