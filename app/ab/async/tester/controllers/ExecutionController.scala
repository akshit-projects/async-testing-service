package ab.async.tester.controllers

import ab.async.tester.library.repository.execution.ExecutionRepository
import ab.async.tester.library.utils.JsonParsers.ResultHelpers
import ab.async.tester.service.flows.FlowExecutionService
import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import io.circe.syntax._
import play.api.Logger
import play.api.mvc._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ExecutionController @Inject()(
                                     cc: ControllerComponents,
                                     executionRepository: ExecutionRepository,
                                     flowExecService: FlowExecutionService
                                   )(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  /** GET /api/v1/executions/:executionId - Get execution details */
  def getExecution(executionId: String): Action[AnyContent] = Action.async { implicit request =>
    executionRepository.findById(executionId).map {
      case Some(execution) => Ok(execution.asJson.noSpaces)
      case None => NotFound(Map("error" -> s"Execution not found: $executionId").asJsonNoSpaces)
    }.recover {
      case ex =>
        logger.error(s"getExecution $executionId failed", ex)
        InternalServerError(Map("error" -> "failed to fetch execution").asJsonNoSpaces)
    }
  }

  /** WebSocket endpoint: /api/v1/executions/:executionId/stream */
  def streamExecution(executionId: String): WebSocket = WebSocket.acceptOrResult[String, String] { _ =>
    logger.info(s"Starting execution stream for executionId: $executionId")
    Future.successful(Right(handleExecutionStream(executionId)))
  }

  private def handleExecutionStream(executionId: String): Flow[String, String, NotUsed] = {
    // Create the execution update stream
    val executionUpdates = flowExecService.streamExecutionUpdates(executionId)

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
            logger.info(s"Execution stream terminated for executionId: $executionId")
            flowExecService.stopExecutionStream(executionId)
          }
        }
    )
  }
}
