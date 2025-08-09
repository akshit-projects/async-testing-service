package ab.async.tester.controllers

import ab.async.tester.domain.resource.ResourceConfig
import ab.async.tester.library.utils.JsonParsers

import javax.inject._
import play.api.mvc._
import io.circe.generic.auto._

import scala.concurrent.{ExecutionContext, Future}
import ab.async.tester.service.resource.ResourceServiceTrait
import ab.async.tester.library.utils.JsonParsers.ResultHelpers
import play.api.Logger
import io.circe.syntax._

@Singleton
class ResourceController @Inject()(
                                    cc: ControllerComponents,
                                    resourceService: ResourceServiceTrait
                                  )(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  def getResources: Action[AnyContent] = Action.async { implicit request =>
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

  def getResource(id: String): Action[AnyContent] = Action.async { implicit request =>
    resourceService.getResourceById(id).map {
      case Some(resource) => Ok(resource.asJson.noSpaces)
      case None => NotFound(Map("error" -> s"resource not found: $id").asJsonNoSpaces)
    } recover {
      case ex =>
        logger.error(s"getResource $id failed", ex)
        InternalServerError(Map("error" -> "failed to fetch resource").asJsonNoSpaces)
    }
  }

  def createResource: Action[AnyContent] = Action.async { implicit request =>
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

  def updateResource(): Action[AnyContent] = Action.async { implicit request =>
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

  def deleteResource(id: String): Action[AnyContent] = Action.async { implicit request =>
    resourceService.deleteResource(id).map { deleted =>
      if (deleted) NoContent else NotFound(Map("error" -> s"resource not found: $id").asJsonNoSpaces)
    } recover {
      case ex =>
        logger.error(s"deleteResource $id failed", ex)
        InternalServerError(Map("error" -> "failed to delete resource").asJsonNoSpaces)
    }
  }

}
