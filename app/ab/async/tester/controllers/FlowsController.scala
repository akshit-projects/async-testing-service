package ab.async.tester.controllers

import ab.async.tester.domain.flow.Floww
import ab.async.tester.library.utils.JsonParsers
import ab.async.tester.library.utils.JsonParsers.ResultHelpers
import ab.async.tester.service.flows.FlowServiceTrait
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.Logger
import play.api.mvc._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FlowsController @Inject()(
                                 cc: ControllerComponents,
                                 flowService: FlowServiceTrait
                               )(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  /** GET /v1/flows?search=&limit=&page=&ids= */
  def getFlows: Action[AnyContent] = Action.async { implicit request =>
    val search = request.getQueryString("search")
    val limit  = request.getQueryString("limit").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(10)
    val page   = request.getQueryString("page").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
    val ids    = request.getQueryString("ids").map(_.split(",").toList)

    // keep controller thin
    flowService.getFlows(search, ids, limit, page).map { flows =>
      Ok(flows.asJson.noSpaces).as("application/json")
    } recover {
      case ex =>
        logger.error("getFlows failed", ex)
        InternalServerError(Map("error" -> "failed to fetch flows").asJsonNoSpaces)
    }
  }

  /** GET /v1/flows/:id */
  def getFlow(id: String): Action[AnyContent] = Action.async { implicit request =>
    flowService.getFlow(id).map {
      case Some(flow) => Ok(flow.asJson.noSpaces).as("application/json")
      case None       => NotFound(Map("error" -> s"Flow not found: $id").asJsonNoSpaces)
    } recover {
      case ex =>
        logger.error(s"getFlow $id failed", ex)
        InternalServerError(Map("error" -> "failed to fetch flow").asJsonNoSpaces)
    }
  }

  /** POST /v1/flows */
  def addFlow(): Action[AnyContent] = Action.async { implicit request =>
    JsonParsers.parseJsonBody[Floww](request)(implicitly, ec) match {
      case Left(result) => Future.successful(result)
      case Right(flow) =>
        // validate minimal invariants
        if (flow.steps.isEmpty) Future.successful(BadRequest(Map("error" -> "flow must have at least one step").asJsonNoSpaces))
        else {
          flowService.addFlow(flow).map { saved =>
            Created(saved.asJson.noSpaces)
          }.recover {
            case ex =>
              logger.error("addFlow failed", ex)
              InternalServerError(Map("error" -> "failed to create flow").asJsonNoSpaces)
          }
        }
    }
  }

  /** PUT /v1/flows */
  def updateFlow(): Action[AnyContent] = Action.async { implicit request =>
    JsonParsers.parseJsonBody[Floww](request)(implicitly, ec) match {
      case Left(result) => Future.successful(result)
      case Right(flow) =>
        flowService.updateFlow(flow).map {
          case true => Ok(Map("status" -> "ok").asJsonNoSpaces).as("application/json")
          case false => NotFound(Map("error" -> "flow not found").asJsonNoSpaces)
        } recover {
          case ex =>
            logger.error("updateFlow failed", ex)
            InternalServerError(Map("error" -> "failed to update flow").asJsonNoSpaces)
        }
    }
  }

  /** POST /v1/flows/validate - validate flow steps */
  def validateFlow(): Action[AnyContent] = Action.async { implicit request =>
    JsonParsers.parseJsonBody[Floww](request)(implicitly, ec) match {
      case Left(result) => Future.successful(result)
      case Right(flow) =>
        flowService.validateSteps(flow)
        Future.successful(Ok(Map("status" -> "valid").asJsonNoSpaces))
    }
  }

  /** GET /v1/flows/:flowId/versions - get all versions of a flow */
  def getFlowVersions(flowId: String): Action[AnyContent] = Action.async { implicit request =>
    flowService.getFlowVersions(flowId).map { versions =>
      Ok(versions.asJson.noSpaces).as("application/json")
    } recover {
      case ex =>
        logger.error(s"getFlowVersions $flowId failed", ex)
        InternalServerError(Map("error" -> "failed to fetch flow versions").asJsonNoSpaces)
    }
  }

  /** GET /v1/flows/:flowId/versions/:version - get a specific version of a flow */
  def getFlowVersion(flowId: String, version: Int): Action[AnyContent] = Action.async { implicit request =>
    flowService.getFlowVersion(flowId, version).map {
      case Some(flowVersion) => Ok(flowVersion.asJson.noSpaces).as("application/json")
      case None => NotFound(Map("error" -> s"Flow version not found: $flowId v$version").asJsonNoSpaces)
    } recover {
      case ex =>
        logger.error(s"getFlowVersion $flowId v$version failed", ex)
        InternalServerError(Map("error" -> "failed to fetch flow version").asJsonNoSpaces)
    }
  }
}
