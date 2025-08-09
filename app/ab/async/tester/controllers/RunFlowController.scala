package ab.async.tester.controllers

import ab.async.tester.domain.requests.RunFlowRequest
import ab.async.tester.library.utils.JsonParsers
import ab.async.tester.library.utils.JsonParsers.ResultHelpers
import ab.async.tester.service.flows.FlowExecutionService
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.Logger
import play.api.mvc._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RunFlowController @Inject()(
                                   cc: ControllerComponents,
                                   flowExecService: FlowExecutionService,
                                )(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  /** POST /api/v1/flows/run - Create execution and publish to Kafka */
  def runFlow(): Action[AnyContent] = Action.async { implicit request =>
    JsonParsers.parseJsonBody[RunFlowRequest](request)(implicitly, ec) match {
      case Left(result) => Future.successful(result)
      case Right(runRequest) =>
        flowExecService.createExecution(runRequest).map { executionResponse =>
          Created(executionResponse.asJson.noSpaces)
        }.recover {
          case ex =>
            logger.error("runFlow failed", ex)
            InternalServerError(Map("error" -> "failed to create execution").asJsonNoSpaces)
        }
    }
  }
}
