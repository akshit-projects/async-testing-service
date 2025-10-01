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

  implicit val listStringColumnType: BaseColumnType[Option[List[String]]] =
    MappedColumnType.base[Option[List[String]], String](
      data => data.getOrElse(List.empty).asJson.noSpaces,
      ts => try {
        if (ts == null || ts.isEmpty) {
          None
        } else {
          DecodingUtils.decodeWithErrorLogs[Option[List[String]]](ts)
        }
      } catch {
        case ex: Exception =>
          // Log the error but don't throw, return None instead
          Logger(this.getClass).error(s"Failed to decode list from JSON: $ts", ex)
          None
      }
    )

  private implicit val logger: Logger = Logger(this.getClass)

  def id          = column[String]("id", O.PrimaryKey)
  def email       = column[String]("email")
  def name        = column[Option[String]]("first_name")
  def bio        = column[Option[String]]("bio")
  def profilePicture  = column[Option[String]]("profile_picture")
  def phoneNumber = column[Option[String]]("phone_number")
  def company     = column[Option[String]]("company")
  def role        = column[String]("role")
  def isAdmin     = column[Boolean]("is_admin")
  def orgIds       = column[Option[List[String]]]("org_ids")
  def teamIds      = column[Option[List[String]]]("team_ids")
  def userUpdatedFields = column[String]("user_updated_fields")
  def lastGoogleSync = column[Option[Long]]("last_google_sync")
  def createdAt   = column[Long]("created_at")
  def modifiedAt  = column[Long]("modified_at")

  def * = (id, email, name, profilePicture, phoneNumber, company, role, bio, isAdmin, orgIds, teamIds, userUpdatedFields, lastGoogleSync, createdAt, modifiedAt) <> (
    {
      case (id, email, name, profilePicture, phoneNumber, company, role, bio, isAdmin, orgIds, teamIds, userUpdatedFields, lastGoogleSync, createdAt, modifiedAt) =>
        User(id, email, name, profilePicture, phoneNumber, company, UserRole.fromString(role), bio, isAdmin, orgIds, teamIds, DecodingUtils.decodeWithErrorLogs[Set[String]](userUpdatedFields), lastGoogleSync, createdAt, modifiedAt)
    },
    (u: User) => {
      Some((u.id, u.email, u.name, u.profilePicture, u.phoneNumber, u.company, u.role.name, u.bio, u.isAdmin, u.orgIds, u.teamIds, u.userUpdatedFields.asJson.noSpaces, u.lastGoogleSync, u.createdAt, u.modifiedAt))
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

  override def findAll(search: Option[String], limit: Int, page: Int): Future[(List[User], Int)] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findAll") {
      val offset = page * limit

      // Build base query
      val baseQuery = search match {
        case Some(searchTerm) =>
          val pattern = s"%$searchTerm%"
          users.filter(u => u.name.like(pattern) || u.email.like(pattern))
        case None =>
          users
      }

      // Count query
      val countQuery = baseQuery.length.result

      // Data query with pagination
      val dataQuery = baseQuery
        .sortBy(_.createdAt.desc)
        .drop(offset)
        .take(limit)
        .result

      // Execute both queries in parallel
      val combinedQuery = for {
        total <- countQuery
        data <- dataQuery
      } yield (data.toList, total)

      db.run(combinedQuery)
    }
  }

  override def updateUserMetadata(
                                   userId: String,
                                   orgIds: Option[List[String]],
                                   teamIds: Option[List[String]],
                                   role: Option[UserRole],
                                   isAdmin: Option[Boolean],
                                   isActive: Option[Boolean]
  ): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updateUserMetadata") {
      // Get current user first
      getUserById(userId).flatMap {
        case Some(user) =>
          // Update only provided fields
          val updatedUser = user.copy(
            orgIds = if (orgIds.isDefined) orgIds else user.orgIds,
            teamIds = if (teamIds.isDefined) teamIds else user.teamIds,
            role = role.getOrElse(user.role),
            isAdmin = isAdmin.getOrElse(user.isAdmin),
            modifiedAt = Instant.now().toEpochMilli
          )

          db.run(users.filter(_.id === userId).update(updatedUser)).map(_ > 0)

        case None =>
          Future.successful(false)
      }
    }
  }
}
