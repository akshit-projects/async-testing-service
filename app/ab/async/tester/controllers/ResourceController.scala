package ab.async.tester.controllers

import ab.async.tester.controllers.auth.AuthorizedAction
import ab.async.tester.domain.resource.ResourceConfig
import ab.async.tester.domain.user.Permissions
import ab.async.tester.library.utils.{JsonParsers, MetricUtils}
import ab.async.tester.library.utils.JsonParsers.ResultHelpers
import ab.async.tester.service.resource.ResourceService
import io.circe.syntax._
import play.api.Logger
import play.api.mvc._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ResourceController @Inject()(
                                    cc: ControllerComponents,
                                    resourceService: ResourceService,
                                    authorizedAction: AuthorizedAction
                                  )(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  def getResources: Action[AnyContent] = authorizedAction.requirePermission(Permissions.RESOURCES_READ).async { implicit request =>
    MetricUtils.withAPIMetrics("getResources") {
      val typesOpt = request.getQueryString("types").map(_.split(",").toList)
      val groupOpt = request.getQueryString("group")
      val namespaceOpt = request.getQueryString("namespace")

      resourceService.getResources(typesOpt, groupOpt, namespaceOpt).map { res =>
        Ok(res.asJson.noSpaces)
      } recover {
        case ex =>
          logger.error("getResources failed", ex)
          InternalServerError(Map("error" -> "failed to list resources").asJsonNoSpaces)
      }
    }
  }

  def getResource(id: String): Action[AnyContent] = authorizedAction.requirePermission(Permissions.RESOURCES_READ).async { implicit request =>
    MetricUtils.withAPIMetrics("getResource") {
      resourceService.getResourceById(id).map {
        case Some(resource) => Ok(resource.asJson.noSpaces)
        case None => NotFound(Map("error" -> s"resource not found: $id").asJsonNoSpaces)
      } recover {
        case ex =>
          logger.error(s"getResource $id failed", ex)
          InternalServerError(Map("error" -> "failed to fetch resource").asJsonNoSpaces)
      }
    }
  }

  def createResource: Action[AnyContent] = authorizedAction.requirePermission(Permissions.RESOURCES_CREATE).async { implicit request =>
    MetricUtils.withAPIMetrics("createResource") {
      JsonParsers.parseJsonBody[ResourceConfig](request)(implicitly, ec) match {
        case Left(result) => Future.successful(result)
        case Right(conf) =>
          resourceService.createResource(conf).map { created =>
            Created(created.asJson.noSpaces)
          } recover {
            case ex =>
              logger.error("createResource failed", ex)
              InternalServerError(Map("error" -> "failed to create resource").asJsonNoSpaces)
          }
      }
    }
  }

  def updateResource(): Action[AnyContent] = authorizedAction.requirePermission(Permissions.RESOURCES_UPDATE).async { implicit request =>
    MetricUtils.withAPIMetrics("updateResource") {
      JsonParsers.parseJsonBody[ResourceConfig](request)(implicitly, ec) match {
        case Left(result) => Future.successful(result)
        case Right(conf) =>
          resourceService.updateResource(conf).map {
            case Some(updated) => Ok(updated.asJson.noSpaces)
            case None => NotFound(Map("error" -> s"resource not found: ${conf.getId}").asJsonNoSpaces)
          } recover {
            case ex =>
              logger.error("updateResource failed", ex)
              InternalServerError(Map("error" -> "failed to update resource").asJsonNoSpaces)
          }
      }
    }
  }

  def deleteResource(id: String): Action[AnyContent] = authorizedAction.requirePermission(Permissions.RESOURCES_DELETE).async { implicit request =>
    MetricUtils.withAPIMetrics("deleteResource") {
      resourceService.deleteResource(id).map { deleted =>
        if (deleted) NoContent else NotFound(Map("error" -> s"resource not found: $id").asJsonNoSpaces)
      } recover {
        case ex =>
          logger.error(s"deleteResource $id failed", ex)
          InternalServerError(Map("error" -> "failed to delete resource").asJsonNoSpaces)
      }
    }
  }

}
