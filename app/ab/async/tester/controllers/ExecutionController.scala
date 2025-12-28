package ab.async.tester.controllers

import ab.async.tester.controllers.auth.{AuthenticatedAction, AuthorizedAction}
import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.response.GenericError
import ab.async.tester.domain.user.Permissions
import ab.async.tester.library.utils.JsonParsers.ResultHelpers
import ab.async.tester.library.utils.MetricUtils
import io.circe.generic.auto._
import ab.async.tester.service.execution.ExecutionsService
import akka.NotUsed
import akka.stream.scaladsl.Flow
import io.circe.syntax._
import play.api.Logger
import play.api.mvc._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ExecutionController @Inject() (
    cc: ControllerComponents,
    executionsService: ExecutionsService,
    authorizedAction: AuthorizedAction
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  /** GET /api/v1/executions/:executionId - Get execution details */
  def getExecution(executionId: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.EXECUTIONS_READ).async {
      implicit request =>
        MetricUtils.withAPIMetrics("getExecution") {
          executionsService
            .getExecutionById(executionId)
            .map {
              case Some(execution) => Ok(execution.asJson.noSpaces)
              case None            =>
                NotFound(
                  Map(
                    "error" -> s"Execution not found: $executionId"
                  ).asJsonNoSpaces
                )
            }
            .recover { case ex =>
              logger.error(s"getExecution $executionId failed", ex)
              InternalServerError(
                GenericError(errorMsg =
                  s"Unable to get execution for id: $executionId"
                ).asJson.noSpaces
              )
            }
        }
    }

  def getExecutions(): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.EXECUTIONS_READ).async {
      implicit request =>
        MetricUtils.withAPIMetrics("getExecutions") {
          val limit = request
            .getQueryString("limit")
            .flatMap(s => scala.util.Try(s.toInt).toOption)
            .getOrElse(10)
          val page = request
            .getQueryString("page")
            .flatMap(s => scala.util.Try(s.toInt).toOption)
            .getOrElse(0)
          val statuses = request
            .getQueryString("status")
            .map(_.split(",").toList.map(_.asInstanceOf[ExecutionStatus]))
          executionsService
            .getExecutions(page, limit, statuses)
            .map { executions =>
              Ok(executions.asJson.noSpaces).as("application/json")
            }
            .recover { case ex =>
              logger.error(s"Unable to get executions", ex)
              InternalServerError(
                GenericError(errorMsg =
                  "Unable to get executions"
                ).asJson.noSpaces
              )
            }
        }
    }

  /** WebSocket endpoint: /api/v1/executions/:executionId/stream */
  def streamExecution(
      executionId: String,
      clientId: Option[String]
  ): WebSocket = WebSocket.acceptOrResult[String, String] { _ =>
    MetricUtils.withAPIMetrics("streamExecution") {
      logger.info(
        s"Starting execution stream for executionId: $executionId and $clientId"
      )
      Future.successful(Right(handleExecutionStream(executionId, clientId)))
    }
  }

  private def handleExecutionStream(
      executionId: String,
      clientId: Option[String]
  ): Flow[String, String, NotUsed] = {
    // Create the execution update stream
    val executionUpdates =
      executionsService.streamExecutionUpdates(executionId, clientId)

    // Handle incoming messages (for potential future use like pause/resume commands)
    val incomingFlow = Flow[String].map { msg =>
      logger.debug(s"Received message for execution $executionId: $msg")
      // For now, we ignore incoming messages but could handle commands here
      msg
    }

    // Combine incoming and outgoing flows
    Flow.fromSinkAndSource(
      sink = incomingFlow.to(akka.stream.scaladsl.Sink.ignore),
      source = executionUpdates
        .watchTermination() { (_, done) =>
          done.foreach { _ =>
            logger.info(
              s"Execution stream terminated for executionId: $executionId"
            )
            executionsService.stopExecutionStream(executionId)
          }
        }
    )
  }
}
