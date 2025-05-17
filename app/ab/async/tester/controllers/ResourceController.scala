package ab.async.tester.controllers

import ab.async.tester.models.resource.ResourceConfig
import ab.async.tester.models.requests.resource.GetResourcesRequest
import ab.async.tester.models.response.GenericError
import ab.async.tester.service.resource.ResourceServiceTrait
import ab.async.tester.utils.{DecodingUtils, MetricUtils}
import io.circe.syntax._
import io.circe.generic.auto._
import play.api.Logger
import play.api.mvc._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ResourceController @Inject()(cc: ControllerComponents, resourceService: ResourceServiceTrait)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private implicit val logger: Logger = Logger(this.getClass)

  /**
   * Get all resources, optionally filtered by type, group, and namespace
   */
  def getResources: Action[AnyContent] = Action.async { request =>
    MetricUtils.withAsyncAPIMetrics("getResources") {
      val filterRequest = {
          val types = request.queryString.get("types").map(typesList => typesList.flatMap(_.split(",")).toList)
          val group = request.queryString.get("group").flatMap(_.headOption)
          val namespace = request.queryString.get("namespace").flatMap(_.headOption)
          
          GetResourcesRequest(types, group, namespace)
      }
      
      resourceService.getResources(filterRequest).map { resources =>
        Ok(resources.asJson.noSpaces)
      }
    }
  }
  
  /**
   * Get a specific resource by ID
   */
  def getResource(id: String): Action[AnyContent] = Action.async { _ =>
    MetricUtils.withAsyncAPIMetrics("getResource") {
      resourceService.getResourceById(id).map {
        case Some(resource) => Ok(resource.asJson.noSpaces)
        case None => NotFound(GenericError(s"Resource not found with ID: $id").asJson.noSpaces)
      }
    }
  }

  /**
   * Create a new resource
   */
  def createResource: Action[AnyContent] = Action.async { request =>
    MetricUtils.withAsyncAPIMetrics("createResource") {
      request.body.asJson match {
        case Some(json) =>
          val resourceConfig = DecodingUtils.decodeJsValue[ResourceConfig](json)
          resourceService.createResource(resourceConfig).map { resource =>
            Created(resource.asJson.noSpaces)
          }.recover {
            case e: Exception =>
              logger.error(s"Error creating resource: ${e.getMessage}", e)
              InternalServerError(Map("message" -> "Failed to create resource").asJson.noSpaces)
          }
        case None =>
          Future.successful(BadRequest(GenericError("Missing request body for creating resource").asJson.noSpaces))
      }
    }
  }
  
  /**
   * Update an existing resource
   */
  def updateResource(): Action[AnyContent] = Action.async { request =>
    MetricUtils.withAsyncAPIMetrics("updateResource") {
      request.body.asJson match {
        case Some(json) =>
          val resourceConfig = DecodingUtils.decodeJsValue[ResourceConfig](json)
          resourceService.updateResource(resourceConfig).map {
            case Some(resource) => Ok(resource.asJson.noSpaces)
            case None => NotFound(GenericError(s"Resource not found with ID: ${resourceConfig.getId}").asJson.noSpaces)
          } recover {
            case e: Exception =>
              logger.error(s"Error updating resource: ${e.getMessage}", e)
              InternalServerError(GenericError("Failed to update resource, please try again later.").asJson.noSpaces)
          }
          
        case None =>
          Future.successful(BadRequest(GenericError("Missing request body").asJson.noSpaces))
      }
    }
  }
  
  /**
   * Delete a resource by ID
   */
  def deleteResource(id: String): Action[AnyContent] = Action.async { _ =>
    MetricUtils.withAsyncAPIMetrics("deleteResource") {
      resourceService.deleteResource(id).map { deleted =>
        if (deleted) {
          NoContent
        } else {
          NotFound(GenericError(s"Resource not found with ID: $id").asJson.noSpaces)
        }
      } recover {
        case e: Exception =>
          logger.error(s"Error deleting resource: ${e.getMessage}", e)
          InternalServerError(GenericError("Failed to delete resource, please try again later").asJson.noSpaces)
      }
    }
  }
}

