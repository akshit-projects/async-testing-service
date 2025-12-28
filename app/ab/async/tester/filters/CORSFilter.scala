package ab.async.tester.filters

import javax.inject.Inject
import play.api.http.HeaderNames
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logger
import play.api.libs.streams.Accumulator
import akka.util.ByteString

/** Filter that adds CORS headers to enable cross-origin requests
  */
class CORSFilter @Inject() (implicit ec: ExecutionContext)
    extends EssentialFilter {
  private val logger = Logger(this.getClass)

  override def apply(next: EssentialAction): EssentialAction = EssentialAction {
    request =>
      // For OPTIONS requests, return a simple 200 OK with CORS headers
      if (request.method == "OPTIONS") {
        logger.debug(s"Handling OPTIONS request to: ${request.path}")
        Accumulator.done(Results.Ok.withHeaders(corsHeaders: _*))
      } else {
        next(request).map { result =>
          // Add CORS headers to all other responses
          result.withHeaders(corsHeaders: _*)
        }
      }
  }

  // Define CORS headers to be applied
  private def corsHeaders = Seq(
    HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN -> "*", // You may want to restrict this to specific origins in production
    HeaderNames.ACCESS_CONTROL_ALLOW_METHODS -> "GET, POST, PUT, DELETE, OPTIONS",
    HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS -> "Origin, X-Requested-With, Content-Type, Accept, Authorization, X-Auth-Token",
    HeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS -> "true",
    HeaderNames.ACCESS_CONTROL_MAX_AGE -> "3600"
  )
}
