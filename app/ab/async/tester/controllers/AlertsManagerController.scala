package ab.async.tester.controllers

import ab.async.tester.controllers.auth.AuthorizedAction
import ab.async.tester.domain.alert.AlertFlowMapping
import ab.async.tester.domain.user.Permissions
import ab.async.tester.library.utils.JsonParsers
import ab.async.tester.library.utils.JsonParsers.ResultHelpers
import ab.async.tester.service.alert.AlertFlowMappingService
import io.circe.syntax._
import play.api.mvc._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AlertsManagerController @Inject()(
                                         cc: ControllerComponents,
                                         alertFlowMappingService: AlertFlowMappingService,
                                         authorizedAction: AuthorizedAction
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  /** POST /api/v1/opsgenie/mappings Create a new mapping
    */
  def createMapping(): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.ALERT_CREATE).async {
      implicit request =>
        JsonParsers.parseJsonBody[AlertFlowMapping](request) match {
          case Left(result)   => Future.successful(result)
          case Right(mapping) =>
            alertFlowMappingService.createAlertFlowMapping(mapping).map { created =>
              Created(created.asJson.noSpaces)
            }
        }
    }

  /** DELETE /api/v1/opsgenie/mappings/:id Delete a mapping
    */
  def deleteMapping(id: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.ALERT_CREATE).async {
      implicit request =>
        alertFlowMappingService.deleteAlertFlowMapping(id).map {
          case true  => NoContent
          case false =>
            NotFound(Map("error" -> s"Mapping not found: $id").asJsonNoSpaces)
        }
    }
}
