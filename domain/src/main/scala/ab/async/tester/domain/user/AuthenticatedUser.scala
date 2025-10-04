package ab.async.tester.domain.user

/**
 * User data for authenticated requests
 * This is a lightweight model used in JWT tokens and request context
 */
case class AuthenticatedUser(
  userId: String,
  email: String,
  name: Option[String],
  role: UserRole,
  isAdmin: Boolean,
  orgIds: Option[List[String]] = None,
  teamIds: Option[List[String]] = None
)

object AuthenticatedUser {
  /**
   * Create from UserAuth and UserProfile
   */
  def fromAuthAndProfile(auth: UserAuth, profile: UserProfile): AuthenticatedUser = {
    AuthenticatedUser(
      userId = auth.id,
      email = auth.email,
      name = profile.name,
      role = profile.role,
      isAdmin = profile.isAdmin,
      orgIds = profile.orgIds,
      teamIds = profile.teamIds
    )
  }

}

