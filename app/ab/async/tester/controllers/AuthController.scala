package ab.async.tester.controllers

import ab.async.tester.domain.auth.ClientToken
import io.circe.generic.auto._
import ab.async.tester.domain.requests.auth.LoginRequest
import ab.async.tester.domain.response.GenericError
import ab.async.tester.library.utils.JsonParsers
import ab.async.tester.service.auth.AuthService
import com.google.inject.{Inject, Singleton}
import io.circe.syntax.EncoderOps
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthController @Inject() (
                                 cc: ControllerComponents,
                                 authService: AuthService, // equivalent of r.service.LoginUser
                                 config: play.api.Configuration
                               )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def loginUser: Action[AnyContent] = Action.async { implicit request =>
    JsonParsers.parseJsonBody[LoginRequest](request)(implicitly, ec) match {
      case Left(result) => Future.successful(result)
      case Right(authReq) =>
        authService.loginUser(authReq).map {
          user =>
            val expiry = Instant.now().plusSeconds(18000).getEpochSecond // 300 mins // TODO change it to use config
            val secret = config.get[String]("jwt.secret")

            val claim = JwtClaim(
              content = user.asJson.noSpaces,
              expiration = Some(expiry)
            )

            Jwt.encode(claim, secret, JwtAlgorithm.HS256) match {
              case token: String =>
                Ok(ClientToken(token, expiry.toString, user).asJson.noSpaces)
            }
        }.recover {
          case ex: Exception =>
            Forbidden(GenericError("Invalid request").asJson.noSpaces)
        }
    }
  }
}
