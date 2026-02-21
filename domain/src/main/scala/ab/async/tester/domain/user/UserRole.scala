package ab.async.tester.domain.user

import io.circe.{Decoder, Encoder}

sealed trait UserRole {
  def name: String
  def permissions: Set[String]
}

/**
 * Permission constants for different operations
 */
object Permissions {
  // Flow permissions
  val FLOWS_READ = "flows:read"
  val FLOWS_CREATE = "flows:create"
  val FLOWS_UPDATE = "flows:update"
  val FLOWS_DELETE = "flows:delete"
  val FLOWS_EXECUTE = "flows:execute"

  val ALERT_READ = "flows:read"
  val ALERT_CREATE = "flows:create"

  val ORG_READ = "org:read"
  val ORG_CREATE = "org:create"
  val ORG_UPDATE = "org:update"
  val ORG_DELETE = "org:delete"

  val TEAMS_READ = "teams:read"
  val TEAMS_CREATE = "teams:create"
  val TEAMS_UPDATE = "teams:update"
  val TEAMS_DELETE = "teams:delete"

  // Execution permissions
  val EXECUTIONS_READ = "executions:read"
  val EXECUTIONS_CREATE = "executions:create"
  val EXECUTIONS_DELETE = "executions:delete"

  // Test suite permissions
  val TESTSUITES_READ = "testsuites:read"
  val TESTSUITES_CREATE = "testsuites:create"
  val TESTSUITES_UPDATE = "testsuites:update"
  val TESTSUITES_DELETE = "testsuites:delete"

  // Resource permissions
  val RESOURCES_READ = "resources:read"
  val RESOURCES_CREATE = "resources:create"
  val RESOURCES_UPDATE = "resources:update"
  val RESOURCES_DELETE = "resources:delete"

  // User permissions
  val USERS_READ = "users:read"
  val USERS_UPDATE = "users:update"
  val USERS_DELETE = "users:delete"

  // Profile permissions
  val PROFILE_READ = "profile:read"
  val PROFILE_UPDATE = "profile:update"

  // Admin permissions
  val ADMIN_ACCESS = "admin:access"
}

object UserRole {
  case object User extends UserRole {
    override def name: String = "user"
    override def permissions: Set[String] = Set(
      Permissions.FLOWS_READ,
      Permissions.FLOWS_CREATE,
      Permissions.FLOWS_UPDATE,
      Permissions.EXECUTIONS_READ,
      Permissions.EXECUTIONS_CREATE,
      Permissions.TESTSUITES_READ,
      Permissions.TESTSUITES_CREATE,
      Permissions.TESTSUITES_UPDATE,
      Permissions.RESOURCES_READ,
      Permissions.PROFILE_READ,
      Permissions.PROFILE_UPDATE,
      Permissions.TEAMS_READ,
      Permissions.ORG_READ,
      Permissions.ORG_CREATE,
      Permissions.TEAMS_CREATE,
      Permissions.FLOWS_EXECUTE,

      Permissions.RESOURCES_CREATE, // TODO remove
    )
  }
  
  case object Admin extends UserRole {
    override def name: String = "admin"
    override def permissions: Set[String] = User.permissions ++ Set(
      Permissions.FLOWS_DELETE,
      Permissions.EXECUTIONS_DELETE,
      Permissions.TESTSUITES_DELETE,
      Permissions.RESOURCES_CREATE,
      Permissions.RESOURCES_DELETE,
      Permissions.USERS_READ,
      Permissions.USERS_UPDATE,
      Permissions.USERS_DELETE,
      Permissions.ADMIN_ACCESS,
    )
  }
  
  def fromString(role: String): UserRole = role.toLowerCase match {
    case "admin" => Admin
    case _ => User
  }
  
  def values: List[UserRole] = List(User, Admin)
  
  implicit val encoder: Encoder[UserRole] = Encoder.encodeString.contramap(_.name)
  implicit val decoder: Decoder[UserRole] = Decoder.decodeString.map(fromString)
}
