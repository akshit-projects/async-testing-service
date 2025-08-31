package ab.async.tester.library.repository.user

import ab.async.tester.domain.user.{User, UserRole}
import ab.async.tester.library.utils.{DecodingUtils, MetricUtils}
import com.google.inject.{Inject, Singleton}
import io.circe.syntax.EncoderOps
import play.api.Logger
import slick.jdbc.H2Profile.Table
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}


class UserTable(tag: Tag) extends Table[User](tag, "users") {

  private implicit val logger: Logger = Logger(this.getClass)

  def id          = column[String]("id", O.PrimaryKey)
  def email       = column[String]("email")
  def name        = column[Option[String]]("name")
  def bio        = column[Option[String]]("bio")
  def profilePicture  = column[Option[String]]("profile_picture")
  def phoneNumber = column[Option[String]]("phone_number")
  def company     = column[Option[String]]("company")
  def role        = column[String]("role")
  def isAdmin     = column[Boolean]("is_admin")
  def userUpdatedFields = column[String]("user_updated_fields")
  def lastGoogleSync = column[Option[Long]]("last_google_sync")
  def createdAt   = column[Long]("created_at")
  def modifiedAt  = column[Long]("modified_at")

  def * = (id, email, name, profilePicture, phoneNumber, company, role, bio, isAdmin, userUpdatedFields, lastGoogleSync, createdAt, modifiedAt) <> (
    {
      case (id, email, name, profilePicture, phoneNumber, company, role, bio, isAdmin, userUpdatedFields, lastGoogleSync, createdAt, modifiedAt) =>
        User(id, email, name, profilePicture, phoneNumber, company, UserRole.fromString(role), bio, isAdmin, DecodingUtils.decodeWithErrorLogs[Set[String]](userUpdatedFields), lastGoogleSync, createdAt, modifiedAt)
    },
    (u: User) => {
      Some((u.id, u.email, u.name, u.profilePicture, u.phoneNumber, u.company, u.role.name, u.bio, u.isAdmin, u.userUpdatedFields.asJson.noSpaces, u.lastGoogleSync, u.createdAt, u.modifiedAt))
    }
  )
}

@Singleton
class UserRepositoryImpl @Inject()(db: Database)(implicit executionContext: ExecutionContext) extends UserRepository {


  private implicit val logger: Logger = Logger(this.getClass)
  private val repositoryName = "UserRepository"

  private val users = TableQuery[UserTable]

  override def upsertUser(user: User): Future[User] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "upsertUser") {
      val updatedUser = user.copy(modifiedAt = Instant.now().toEpochMilli)
      db.run(users.insertOrUpdate(updatedUser)).map(_ => updatedUser)
    }
  }

  override def getUser(email: String): Future[Option[User]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "getUser") {
      db.run(users.filter(_.email === email).result.headOption)
    }
  }

  override def getUserById(id: String): Future[Option[User]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "getUserById") {
      db.run(users.filter(_.id === id).result.headOption)
    }
  }

  override def updateUserProfile(user: User): Future[User] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updateUserProfile") {
      val updatedUser = user.copy(modifiedAt = Instant.now().toEpochMilli)
      db.run(users.filter(_.id === user.id).update(updatedUser)).map(_ => updatedUser)
    }
  }

  override def updateUserRole(userId: String, role: UserRole, isAdmin: Boolean): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updateUserRole") {
      val updateQuery = users.filter(_.id === userId)
        .map(u => (u.role, u.isAdmin, u.modifiedAt))
        .update((role.name, isAdmin, Instant.now().toEpochMilli))

      db.run(updateQuery).map(_ > 0)
    }
  }
}
