package ab.async.tester.controllers

import ab.async.tester.controllers.auth.AuthorizedAction
import ab.async.tester.domain.flow.Floww
import ab.async.tester.domain.requests.RunFlowRequest
import ab.async.tester.domain.response.flow.ImportFlowsResponse
import ab.async.tester.domain.user.Permissions
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.utils.{JsonParsers, MetricUtils}
import ab.async.tester.library.utils.JsonParsers.ResultHelpers
import ab.async.tester.service.flows.FlowService
import io.circe.generic.auto._
import io.circe.jawn
import io.circe.syntax._
import play.api.Logger
import play.api.mvc._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FlowsController @Inject() (
    cc: ControllerComponents,
    flowService: FlowService,
    authorizedAction: AuthorizedAction
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  /** GET /v1/flows?search=&limit=&page=&ids=&orgId=&teamId= */
  def getFlows(orgId: Option[String]): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.FLOWS_READ).async {
      implicit request =>
        MetricUtils.withAPIMetrics("getFlows") {
          val search = request.getQueryString("search")
          val limit = request
            .getQueryString("limit")
            .flatMap(s => scala.util.Try(s.toInt).toOption)
            .getOrElse(10)
          val page = request
            .getQueryString("page")
            .flatMap(s => scala.util.Try(s.toInt).toOption)
            .getOrElse(0)
          val ids = request.getQueryString("ids").map(_.split(",").toList)
          val teamId = request.getQueryString("teamId")
          val stepTypes =
            request.getQueryString("stepTypes").map(_.split(",").toList)

          // keep controller thin
          flowService
            .getFlows(search, ids, orgId, teamId, stepTypes, limit, page)
            .map { paginatedFlows =>
              Ok(paginatedFlows.asJson.noSpaces).as("application/json")
            } recover { case ex =>
            logger.error("getFlows failed", ex)
            InternalServerError(
              Map("error" -> "failed to fetch flows").asJsonNoSpaces
            )
          }
        }
    }

  /** GET /v1/flows/:id */
  def getFlow(id: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.FLOWS_READ).async {
      implicit request =>
        MetricUtils.withAPIMetrics("getFlow") {
          flowService.getFlow(id).map {
            case Some(flow) => Ok(flow.asJson.noSpaces).as("application/json")
            case None       =>
              NotFound(Map("error" -> s"Flow not found: $id").asJsonNoSpaces)
          } recover { case ex =>
            logger.error(s"getFlow $id failed", ex)
            InternalServerError(
              Map("error" -> "failed to fetch flow").asJsonNoSpaces
            )
          }
        }
    }

  /** POST /v1/flows */
  def addFlow(): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.FLOWS_CREATE).async {
      implicit request =>
        MetricUtils.withAPIMetrics("addFlow") {
          JsonParsers.parseJsonBody[Floww](request)(implicitly, ec) match {
            case Left(result) => Future.successful(result)
            case Right(flow)  =>
              // validate minimal invariants
              if (flow.steps.isEmpty)
                Future.successful(
                  BadRequest(
                    Map(
                      "error" -> "flow must have at least one step"
                    ).asJsonNoSpaces
                  )
                )
              else {
                flowService
                  .addFlow(flow)
                  .map { saved =>
                    Created(saved.asJson.noSpaces)
                  }
                  .recover { case ex =>
                    logger.error("addFlow failed", ex)
                    InternalServerError(
                      Map("error" -> "failed to create flow").asJsonNoSpaces
                    )
                  }
              }
          }
        }
    }

  /** PUT /v1/flows */
  def updateFlow(): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.FLOWS_UPDATE).async {
      implicit request =>
        MetricUtils.withAPIMetrics("updateFlow") {
          JsonParsers.parseJsonBody[Floww](request)(implicitly, ec) match {
            case Left(result) => Future.successful(result)
            case Right(flow)  =>
              flowService.updateFlow(flow).map {
                case true =>
                  Ok(Map("status" -> "ok").asJsonNoSpaces)
                    .as("application/json")
                case false =>
                  NotFound(Map("error" -> "flow not found").asJsonNoSpaces)
              } recover { case ex =>
                logger.error("updateFlow failed", ex)
                InternalServerError(
                  Map("error" -> "failed to update flow").asJsonNoSpaces
                )
              }
          }
        }
    }

  /** POST /v1/flows/validate - validate flow steps */
  def validateFlow(): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.FLOWS_READ).async {
      implicit request =>
        MetricUtils.withAPIMetrics("validateFlow") {
          JsonParsers.parseJsonBody[Floww](request)(implicitly, ec) match {
            case Left(result) => Future.successful(result)
            case Right(flow)  =>
              flowService.validateFlow(flow)
              Future.successful(Ok(Map("status" -> "valid").asJsonNoSpaces))
          }
        }
    }

  /** GET /v1/flows/:flowId/versions?limit=&page= - get all versions of a flow
    */
  def getFlowVersions(flowId: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.FLOWS_READ).async {
      implicit request =>
        MetricUtils.withAPIMetrics("getFlowVersions") {
          val limit = request
            .getQueryString("limit")
            .flatMap(s => scala.util.Try(s.toInt).toOption)
            .getOrElse(10)
          val page = request
            .getQueryString("page")
            .flatMap(s => scala.util.Try(s.toInt).toOption)
            .getOrElse(0)

          flowService.getFlowVersions(flowId, limit, page).map {
            paginatedVersions =>
              Ok(paginatedVersions.asJson.noSpaces).as("application/json")
          } recover { case ex =>
            logger.error(s"getFlowVersions $flowId failed", ex)
            InternalServerError(
              Map("error" -> "failed to fetch flow versions").asJsonNoSpaces
            )
          }
        }
    }

  /** GET /v1/flows/:flowId/versions/:version - get a specific version of a flow
    */
  def getFlowVersion(flowId: String, version: Int): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.FLOWS_READ).async {
      implicit request =>
        MetricUtils.withAPIMetrics("getFlowVersion") {
          flowService.getFlowVersion(flowId, version).map {
            case Some(flowVersion) =>
              Ok(flowVersion.asJson.noSpaces).as("application/json")
            case None =>
              NotFound(
                Map(
                  "error" -> s"Flow version not found: $flowId v$version"
                ).asJsonNoSpaces
              )
          } recover { case ex =>
            logger.error(s"getFlowVersion $flowId v$version failed", ex)
            InternalServerError(
              Map("error" -> "failed to fetch flow version").asJsonNoSpaces
            )
          }
        }
    }

  /** POST /api/v1/flows/run - Create execution and publish to Kafka */
  def runFlow(): Action[AnyContent] = authorizedAction
    .requirePermission(Permissions.EXECUTIONS_CREATE)
    .async { implicit request =>
      JsonParsers.parseJsonBody[RunFlowRequest](request)(implicitly, ec) match {
        case Left(result)      => Future.successful(result)
        case Right(runRequest) =>
          flowService
            .createExecution(runRequest)
            .map { executionResponse =>
              Created(executionResponse.asJson.noSpaces)
            }
            .recover { case ex =>
              logger.error("runFlow failed", ex)
              InternalServerError(s"Run flow failed due to ${ex.getMessage}")
            }
      }
    }

  /** GET /v1/flows/export?ids=&orgId=&teamId= */
  def exportFlows(): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.FLOWS_READ).async {
      implicit request =>
        MetricUtils.withAPIMetrics("exportFlows") {
          val ids = request.getQueryString("ids").map(_.split(",").toList)
          val orgId = request.getQueryString("orgId")
          val teamId = request.getQueryString("teamId")

          flowService.exportFlows(ids, orgId, teamId).map { flows =>
            Ok(flows.asJson.noSpaces)
              .as("application/json")
              .withHeaders(
                "Content-Disposition" -> "attachment; filename=flows_export.json"
              )
          } recover { case ex =>
            logger.error("exportFlows failed", ex)
            InternalServerError(
              Map("error" -> "failed to export flows").asJsonNoSpaces
            )
          }
        }
    }

  /** POST /v1/flows/import */
  def importFlows()
      : Action[MultipartFormData[play.api.libs.Files.TemporaryFile]] =
    authorizedAction
      .requirePermission(Permissions.FLOWS_CREATE)
      .async(parse.multipartFormData) { implicit request =>
        MetricUtils.withAPIMetrics("importFlows") {
          request.body.file("file") match {
            case Some(filePart) =>
              val file = filePart.ref.path.toFile
              val source = scala.io.Source.fromFile(file)
              val content =
                try source.mkString
                finally source.close()

              jawn.decode[List[Floww]](content) match {
                case Right(flows) =>
                  val creatorId = request.user.userId

                  flowService.importFlows(flows, creatorId).map { imported =>
                    Ok(
                      ImportFlowsResponse(s"Successfully imported ${imported.size} flows", imported.size).asJson.noSpaces
                    ).as("application/json")
                  } recover {
                    case ex: ValidationException =>
                      BadRequest(Map("error" -> ex.getMessage).asJsonNoSpaces)
                    case ex =>
                      logger.error("importFlows failed", ex)
                      InternalServerError(
                        Map("error" -> "failed to import flows").asJsonNoSpaces
                      )
                  }
                case Left(error) =>
                  Future.successful(
                    BadRequest(
                      Map(
                        "error" -> s"Invalid JSON format: $error"
                      ).asJsonNoSpaces
                    )
                  )
              }
            case None =>
              Future.successful(
                BadRequest(Map("error" -> "Missing file").asJsonNoSpaces)
              )
          }
        }
      }
}
