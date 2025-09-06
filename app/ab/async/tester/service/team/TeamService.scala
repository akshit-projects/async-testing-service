package ab.async.tester.service.team

import ab.async.tester.domain.common.{PaginatedResponse, PaginationMetadata}
import ab.async.tester.domain.team.Team
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.repository.organisation.OrganisationRepository
import ab.async.tester.library.repository.team.TeamRepository
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TeamServiceImpl])
trait TeamService {
  def createTeam(team: Team): Future[Team]
  def getTeam(id: String): Future[Option[Team]]
  def getTeams(search: Option[String], orgId: Option[String], limit: Int, page: Int): Future[PaginatedResponse[Team]]
  def getTeamsByOrganisation(orgId: String): Future[List[Team]]
  def updateTeam(team: Team): Future[Boolean]
  def deleteTeam(id: String): Future[Boolean]
}

@Singleton
class TeamServiceImpl @Inject()(
  teamRepository: TeamRepository,
  organisationRepository: OrganisationRepository
)(implicit ec: ExecutionContext) extends TeamService {

  private implicit val logger: Logger = Logger(this.getClass)
  private val serviceName = "TeamService"

  override def createTeam(team: Team): Future[Team] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "createTeam") {
      validateTeam(team).flatMap { _ =>
        // Check if organisation exists
        organisationRepository.findById(team.orgId).flatMap {
          case None =>
            Future.failed(ValidationException(s"Organisation with ID '${team.orgId}' does not exist"))
          case Some(_) =>
            // Check if team name already exists in the organisation
            teamRepository.findByNameAndOrg(team.name, team.orgId).flatMap {
              case Some(_) =>
                Future.failed(ValidationException(s"Team with name '${team.name}' already exists in this organisation"))
              case None =>
                val now = System.currentTimeMillis() / 1000
                val newTeam = team.copy(createdAt = now, modifiedAt = now)
                teamRepository.insert(newTeam)
            }
        }
      }
    }

  override def getTeam(id: String): Future[Option[Team]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getTeam") {
      teamRepository.findById(id).recover {
        case e: Exception =>
          logger.error(s"Error retrieving team $id: ${e.getMessage}", e)
          None
      }
    }

  override def getTeams(search: Option[String], orgId: Option[String], limit: Int, page: Int): Future[PaginatedResponse[Team]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getTeams") {
      teamRepository.findAllWithCount(search, orgId, limit, page).map { case (teams, total) =>
        PaginatedResponse(
          data = teams,
          pagination = PaginationMetadata(page, limit, total)
        )
      }.recover {
        case e: Exception =>
          logger.error(s"Error retrieving teams: ${e.getMessage}", e)
          PaginatedResponse(Nil, PaginationMetadata(page, limit, 0))
      }
    }

  override def getTeamsByOrganisation(orgId: String): Future[List[Team]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getTeamsByOrganisation") {
      teamRepository.findByOrganisation(orgId).recover {
        case e: Exception =>
          logger.error(s"Error retrieving teams for organisation $orgId: ${e.getMessage}", e)
          Nil
      }
    }

  override def updateTeam(team: Team): Future[Boolean] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "updateTeam") {
      validateTeam(team).flatMap { _ =>
        team.id match {
          case Some(id) =>
            // Check if organisation exists
            organisationRepository.findById(team.orgId).flatMap {
              case None =>
                Future.failed(ValidationException(s"Organisation with ID '${team.orgId}' does not exist"))
              case Some(_) =>
                // Check if another team with the same name exists in the organisation (excluding current one)
                teamRepository.findByNameAndOrg(team.name, team.orgId).flatMap {
                  case Some(existing) if existing.id != team.id =>
                    Future.failed(ValidationException(s"Team with name '${team.name}' already exists in this organisation"))
                  case _ =>
                    teamRepository.update(team)
                }
            }
          case None =>
            Future.failed(ValidationException("Team ID is required for update"))
        }
      }
    }

  override def deleteTeam(id: String): Future[Boolean] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "deleteTeam") {
      teamRepository.delete(id).recover {
        case e: Exception =>
          logger.error(s"Error deleting team $id: ${e.getMessage}", e)
          false
      }
    }

  private def validateTeam(team: Team): Future[Unit] = {
    if (team.name.trim.isEmpty) {
      Future.failed(ValidationException("Team name cannot be empty"))
    } else if (team.name.length > 255) {
      Future.failed(ValidationException("Team name cannot exceed 255 characters"))
    } else if (team.orgId.trim.isEmpty) {
      Future.failed(ValidationException("Organisation ID cannot be empty"))
    } else {
      Future.successful(())
    }
  }
}
