package ab.async.tester.domain.user

/**
 * Sensitive authentication data - should never be exposed in API responses
 */
case class UserAuth(
  id: String,
  email: String,
  phoneNumber: Option[String] = None,
  passwordHash: Option[String] = None,
  authToken: Option[String] = None,
  refreshToken: Option[String] = None,
  tokenExpiresAt: Option[Long] = None,
  googleId: Option[String] = None,
  lastLoginAt: Option[Long] = None,
  passwordResetToken: Option[String] = None,
  passwordResetExpiresAt: Option[Long] = None,
  emailVerified: Boolean = false,
  createdAt: Long,
  updatedAt: Long
)
