package ab.async.tester.controllers

import ab.async.tester.controllers.auth.AuthorizedAction
import ab.async.tester.domain.callbacks.OpsgeniePayload
import ab.async.tester.library.utils.JsonParsers
import ab.async.tester.library.utils.JsonParsers.ResultHelpers
import ab.async.tester.service.opsgenie.OpsgenieService
import play.api.Logger
import play.api.mvc._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AlertCallbackController @Inject()(
                                         cc: ControllerComponents,
                                         opsgenieService: OpsgenieService,
                                       )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  /**
   * POST /api/v1/webhooks/opsgenie Receives Opsgenie webhook alerts
   */
  def opsgenieWebhook(): Action[AnyContent] = Action.async { implicit request =>
    JsonParsers.parseJsonBody[OpsgeniePayload](request)(implicitly, ec) match {
      case Left(result)   => Future.successful(result)
      case Right(payload) =>
        opsgenieService
          .handleWebhook(payload)
          .map { _ =>
            Accepted(Map("status" -> "webhook received").asJsonNoSpaces)
          }
          .recover { case ex =>
            logger.error("Error processing Opsgenie webhook", ex)
            InternalServerError(
              Map("error" -> "failed to process webhook").asJsonNoSpaces
            )
          }
    }
  }
}
