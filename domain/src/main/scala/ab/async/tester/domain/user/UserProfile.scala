package ab.async.tester.domain.user

/**
 * Dao for user profile containing non-sensitive data.
 */
case class UserProfile(
  id: String,
  name: Option[String] = None,
  bio: Option[String] = None,
  profilePicture: Option[String] = None,
  company: Option[String] = None,
  role: UserRole = UserRole.User,
  isAdmin: Boolean = false,
  isActive: Boolean = true,
  orgIds: Option[List[String]] = None,
  teamIds: Option[List[String]] = None,
  userUpdatedFields: Set[String] = Set.empty,
  lastGoogleSync: Option[Long] = None,
  createdAt: Long,
  updatedAt: Long
)

/**
 * Public user data - minimal information for API responses
 * Does not include any PII
 */
case class PublicUserProfile(
  id: String,
  name: Option[String] = None,
  profilePicture: Option[String] = None,
  company: Option[String] = None,
  role: String,
  isAdmin: Boolean = false,
  orgIds: Option[List[String]] = None,
  teamIds: Option[List[String]] = None,
  createdAt: Long,
  updatedAt: Long
)

/**
 * Detailed user data for admin APIs - includes email but not other sensitive data
 */
case class DetailedUserProfile(
  id: String,
  email: String,
  name: Option[String] = None,
  profilePicture: Option[String] = None,
  company: Option[String] = None,
  role: String,
  isAdmin: Boolean = false,
  isActive: Boolean = true,
  orgIds: Option[List[String]] = None,
  teamIds: Option[List[String]] = None,
  lastLoginAt: Option[Long] = None,
  createdAt: Long,
  updatedAt: Long
)