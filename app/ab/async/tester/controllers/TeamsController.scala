package ab.async.tester.controllers

import ab.async.tester.controllers.auth.AuthorizedAction
import ab.async.tester.domain.team.Team
import ab.async.tester.domain.user.Permissions
import ab.async.tester.library.utils.JsonParsers
import ab.async.tester.service.team.TeamService
import com.google.inject.{Inject, Singleton}
import io.circe.syntax._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamsController @Inject() (
    cc: ControllerComponents,
    authorizedAction: AuthorizedAction,
    teamService: TeamService
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  /** POST /v1/teams - create a new team */
  def createTeam(): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.TEAMS_CREATE).async {
      implicit request =>
        JsonParsers.parseJsonBody[Team](request)(implicitly, ec) match {
          case Left(result) => Future.successful(result)
          case Right(team)  =>
            teamService
              .createTeam(team)
              .map { created =>
                Created(created.asJson.noSpaces).as("application/json")
              }
              .recover { case e: Exception =>
                BadRequest(Map("error" -> e.getMessage).asJson.noSpaces)
                  .as("application/json")
              }
        }
    }

  /** GET /v1/teams/:id - get team by ID */
  def getTeam(id: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.TEAMS_READ).async {
      implicit request =>
        teamService.getTeam(id).map {
          case Some(team) =>
            Ok(team.asJson.noSpaces).as("application/json")
          case None =>
            NotFound(Map("error" -> "Team not found").asJson.noSpaces)
              .as("application/json")
        }
    }

  /** GET /v1/teams - get all teams with optional filters */
  def getTeams(
      search: Option[String],
      orgId: Option[String],
      limit: Option[Int],
      page: Option[Int]
  ): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.TEAMS_READ).async {
      implicit request =>
        val actualLimit = limit.getOrElse(50).min(100)
        val actualPage = page.getOrElse(0).max(0)

        teamService.getTeams(search, orgId, actualLimit, actualPage).map {
          paginatedTeams =>
            Ok(paginatedTeams.asJson.noSpaces).as("application/json")
        }
    }

  /** GET /v1/organisations/:orgId/teams - get teams by organisation */
  def getTeamsByOrganisation(orgId: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.TEAMS_READ).async {
      implicit request =>
        teamService.getTeamsByOrganisation(orgId).map { teams =>
          Ok(teams.asJson.noSpaces).as("application/json")
        }
    }

  /** PUT /v1/teams/:id - update team */
  def updateTeam(id: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.TEAMS_UPDATE).async {
      implicit request =>
        JsonParsers.parseJsonBody[Team](request)(implicitly, ec) match {
          case Left(result) => Future.successful(result)
          case Right(team)  =>
            val teamWithId = team.copy(id = Some(id))
            teamService
              .updateTeam(teamWithId)
              .map { updated =>
                if (updated) {
                  Ok(Map("success" -> true).asJson.noSpaces)
                    .as("application/json")
                } else {
                  NotFound(Map("error" -> "Team not found").asJson.noSpaces)
                    .as("application/json")
                }
              }
              .recover { case e: Exception =>
                BadRequest(Map("error" -> e.getMessage).asJson.noSpaces)
                  .as("application/json")
              }
        }
    }

  /** DELETE /v1/teams/:id - delete team */
  def deleteTeam(id: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.TEAMS_DELETE).async {
      implicit request =>
        teamService.deleteTeam(id).map { deleted =>
          if (deleted) {
            Ok(Map("success" -> true).asJson.noSpaces).as("application/json")
          } else {
            NotFound(Map("error" -> "Team not found").asJson.noSpaces)
              .as("application/json")
          }
        }
    }
}
