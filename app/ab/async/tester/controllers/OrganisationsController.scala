package ab.async.tester.controllers

import ab.async.tester.controllers.auth.AuthorizedAction
import ab.async.tester.domain.organisation.Organisation
import ab.async.tester.domain.user.Permissions
import ab.async.tester.library.utils.JsonParsers
import ab.async.tester.service.organisation.OrganisationService
import com.google.inject.{Inject, Singleton}
import io.circe.syntax._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OrganisationsController @Inject() (
    cc: ControllerComponents,
    authorizedAction: AuthorizedAction,
    organisationService: OrganisationService
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  /** POST /v1/organisations - create a new organisation */
  def createOrganisation(): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.ORG_CREATE).async {
      implicit request =>
        JsonParsers.parseJsonBody[Organisation](request)(implicitly, ec) match {
          case Left(result)        => Future.successful(result)
          case Right(organisation) =>
            organisationService
              .createOrganisation(organisation)
              .map { created =>
                Created(created.asJson.noSpaces).as("application/json")
              }
              .recover { case e: Exception =>
                BadRequest(Map("error" -> e.getMessage).asJson.noSpaces)
                  .as("application/json")
              }
        }
    }

  /** GET /v1/organisations/:id - get organisation by ID */
  def getOrganisation(id: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.ORG_READ).async {
      implicit request =>
        organisationService.getOrganisation(id).map {
          case Some(organisation) =>
            Ok(organisation.asJson.noSpaces).as("application/json")
          case None =>
            NotFound(Map("error" -> "Organisation not found").asJson.noSpaces)
              .as("application/json")
        }
    }

  /** GET /v1/organisations - get all organisations with optional search */
  def getOrganisations(
      search: Option[String],
      limit: Option[Int],
      page: Option[Int]
  ): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.ORG_READ).async {
      implicit request =>
        val actualLimit = limit.getOrElse(50).min(100)
        val actualPage = page.getOrElse(0).max(0)

        organisationService
          .getOrganisations(search, actualLimit, actualPage)
          .map { paginatedOrganisations =>
            Ok(paginatedOrganisations.asJson.noSpaces).as("application/json")
          }
    }

  /** PUT /v1/organisations/:id - update organisation */
  def updateOrganisation(id: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.ORG_UPDATE).async {
      implicit request =>
        JsonParsers.parseJsonBody[Organisation](request)(implicitly, ec) match {
          case Left(result)        => Future.successful(result)
          case Right(organisation) =>
            val orgWithId = organisation.copy(id = Some(id))
            organisationService
              .updateOrganisation(orgWithId)
              .map { updated =>
                if (updated) {
                  Ok(Map("success" -> true).asJson.noSpaces)
                    .as("application/json")
                } else {
                  NotFound(
                    Map("error" -> "Organisation not found").asJson.noSpaces
                  ).as("application/json")
                }
              }
              .recover { case e: Exception =>
                BadRequest(Map("error" -> e.getMessage).asJson.noSpaces)
                  .as("application/json")
              }
        }
    }

  /** DELETE /v1/organisations/:id - delete organisation */
  def deleteOrganisation(id: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.ORG_DELETE).async {
      implicit request =>
        organisationService.deleteOrganisation(id).map { deleted =>
          if (deleted) {
            Ok(Map("success" -> true).asJson.noSpaces).as("application/json")
          } else {
            NotFound(Map("error" -> "Organisation not found").asJson.noSpaces)
              .as("application/json")
          }
        }
    }
}
