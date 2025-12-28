package ab.async.tester.errors

import javax.inject._
import play.api._
import play.api.mvc._

import scala.concurrent._
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.Results._
import play.api.libs.json.Json
import play.api.routing.Router

@Singleton
class GlobalErrorHandler @Inject() (
    env: Environment,
    config: Configuration,
    sourceMapper: OptionalSourceMapper,
    router: Provider[Router]
)(implicit ec: ExecutionContext)
    extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {

  private val logger = Logger(this.getClass)

  private def jsonError(code: Int, message: String) =
    Results.Status(code)(Json.obj("error" -> message))

  override def onServerError(
      request: RequestHeader,
      exception: Throwable
  ): Future[Result] = {
    // log structured
    logger.error(
      s"Unhandled exception for ${request.method} ${request.uri}",
      exception
    )
    super.onServerError(request, exception).recover { case _ =>
      jsonError(500, "Internal server error")
    }
  }

  override def onClientError(
      request: RequestHeader,
      statusCode: Int,
      message: String
  ): Future[Result] = {
    Future.successful(
      jsonError(statusCode, if (message.nonEmpty) message else "Client error")
    )
  }
}
