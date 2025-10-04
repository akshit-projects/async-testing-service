package ab.async.tester.library.repository.user

import ab.async.tester.domain.user.{UserProfile, UserRole}
import ab.async.tester.library.utils.{DecodingUtils, MetricUtils}
import com.google.inject.{ImplementedBy, Inject, Singleton}
import io.circe.syntax.EncoderOps
import play.api.Logger
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class UserProfileTable(tag: Tag) extends Table[UserProfile](tag, "user_profiles") {

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
          Logger(this.getClass).error(s"Failed to decode list from JSON: $ts", ex)
          None
      }
    )

  private implicit val logger: Logger = Logger(this.getClass)

  def id = column[String]("id", O.PrimaryKey)
  def name = column[Option[String]]("name")
  def bio = column[Option[String]]("bio")
  def profilePicture = column[Option[String]]("profile_picture")
  def company = column[Option[String]]("company")
  def role = column[String]("role")
  def isAdmin = column[Boolean]("is_admin")
  def isActive = column[Boolean]("is_active")
  def orgIds = column[Option[List[String]]]("org_ids")
  def teamIds = column[Option[List[String]]]("team_ids")
  def userUpdatedFields = column[String]("user_updated_fields")
  def lastGoogleSync = column[Option[Long]]("last_google_sync")
  def createdAt = column[Long]("created_at")
  def updatedAt = column[Long]("updated_at")

  def * = (id, name, bio, profilePicture, company, role, isAdmin, isActive, orgIds, teamIds, userUpdatedFields, lastGoogleSync, createdAt, updatedAt) <> (
    {
      case (id, name, bio, profilePicture, company, role, isAdmin, isActive, orgIds, teamIds, userUpdatedFields, lastGoogleSync, createdAt, updatedAt) =>
        UserProfile(id, name, bio, profilePicture, company, UserRole.fromString(role), isAdmin, isActive, orgIds, teamIds, DecodingUtils.decodeWithErrorLogs[Set[String]](userUpdatedFields), lastGoogleSync, createdAt, updatedAt)
    },
    (p: UserProfile) => {
      Some((p.id, p.name, p.bio, p.profilePicture, p.company, p.role.name, p.isAdmin, p.isActive, p.orgIds, p.teamIds, p.userUpdatedFields.asJson.noSpaces, p.lastGoogleSync, p.createdAt, p.updatedAt))
    }
  )
}

@ImplementedBy(classOf[UserProfileRepositoryImpl])
trait UserProfileRepository {
  def findById(id: String): Future[Option[UserProfile]]
  def findAll(search: Option[String], limit: Int, page: Int): Future[(List[UserProfile], Int)]
  def insert(profile: UserProfile): Future[UserProfile]
  def update(profile: UserProfile): Future[Boolean]
  def updateMetadata(id: String, orgIds: Option[List[String]], teamIds: Option[List[String]], role: Option[UserRole], isAdmin: Option[Boolean], isActive: Option[Boolean]): Future[Boolean]
}

@Singleton
class UserProfileRepositoryImpl @Inject()(db: Database)(implicit ec: ExecutionContext) extends UserProfileRepository {

  private val repositoryName = "UserProfileRepository"
  private val userProfiles = TableQuery[UserProfileTable]
  implicit private val logger: Logger = Logger(this.getClass)

  override def findById(id: String): Future[Option[UserProfile]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findById") {
      db.run(userProfiles.filter(_.id === id).result.headOption)
    }
  }

  override def findAll(search: Option[String], limit: Int, page: Int): Future[(List[UserProfile], Int)] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findAll") {
      val offset = page * limit
      
      // Build base query
      val baseQuery = search match {
        case Some(searchTerm) =>
          val pattern = s"%$searchTerm%"
          userProfiles.filter(p => p.name.like(pattern))
        case None =>
          userProfiles
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

  override def insert(profile: UserProfile): Future[UserProfile] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "insert") {
      val now = Instant.now().toEpochMilli
      val profileWithTimestamps = profile.copy(createdAt = now, updatedAt = now)
      db.run(userProfiles += profileWithTimestamps).map(_ => profileWithTimestamps)
    }
  }

  override def update(profile: UserProfile): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "update") {
      val updatedProfile = profile.copy(updatedAt = Instant.now().toEpochMilli)
      db.run(userProfiles.filter(_.id === profile.id).update(updatedProfile)).map(_ > 0)
    }
  }

  override def updateMetadata(
    id: String,
    orgIds: Option[List[String]],
    teamIds: Option[List[String]],
    role: Option[UserRole],
    isAdmin: Option[Boolean],
    isActive: Option[Boolean]
  ): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updateMetadata") {
      // Get current profile first
      findById(id).flatMap {
        case Some(profile) =>
          // Update only provided fields
          val updatedProfile = profile.copy(
            orgIds = if (orgIds.isDefined) orgIds else profile.orgIds,
            teamIds = if (teamIds.isDefined) teamIds else profile.teamIds,
            role = role.getOrElse(profile.role),
            isAdmin = isAdmin.getOrElse(profile.isAdmin),
            isActive = isActive.getOrElse(profile.isActive),
            updatedAt = Instant.now().toEpochMilli
          )
          
          db.run(userProfiles.filter(_.id === id).update(updatedProfile)).map(_ > 0)
          
        case None =>
          Future.successful(false)
      }
    }
  }
}
