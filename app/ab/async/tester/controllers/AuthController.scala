package ab.async.tester.controllers

import ab.async.tester.domain.auth._
import ab.async.tester.domain.requests.auth.LoginRequest
import ab.async.tester.domain.response.GenericError
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.utils.{JsonParsers, MetricUtils}
import ab.async.tester.service.auth.AuthService
import com.google.inject.{Inject, Singleton}
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import play.api.Logger
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthController @Inject() (
  cc: ControllerComponents,
  authService: AuthService,
)(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  def googleLogin: Action[AnyContent] = Action.async { implicit request =>
    MetricUtils.withAPIMetrics("googleLogin") {
      JsonParsers.parseJsonBody[LoginRequest](request)(implicitly, ec) match {
        case Left(result) => Future.successful(result)
        case Right(authReq) =>
          authService.loginWithGoogle(authReq).map { authResponse =>
            Ok(authResponse.asJson.noSpaces).as("application/json")
          }.recover {
            case ex: Exception =>
              logger.error(s"Google login failed: ${ex.getMessage}", ex)
              Forbidden(GenericError("Invalid request").asJson.noSpaces)
          }
      }
    }
  }

  def loginWithEmail(): Action[AnyContent] = Action.async { implicit request =>
    MetricUtils.withAPIMetrics("loginWithEmail") {
      JsonParsers.parseJsonBody[EmailLoginRequest](request) match {
        case Left(result) => Future.successful(result)
        case Right(loginRequest) =>
          authService.loginWithEmail(loginRequest.email, loginRequest.password).map { authResponse =>
            Ok(authResponse.asJson.noSpaces).as("application/json")
          }.recover {
            case e: ValidationException =>
              logger.warn(s"Login failed: ${e.getMessage}")
              Unauthorized(GenericError(e.getMessage).asJson.noSpaces).as("application/json")
            case e: Exception =>
              logger.error(s"Login error: ${e.getMessage}", e)
              InternalServerError(GenericError("Login failed").asJson.noSpaces).as("application/json")
          }
      }
    }
  }

  def register(): Action[AnyContent] = Action.async { implicit request =>
    MetricUtils.withAPIMetrics("register") {
      JsonParsers.parseJsonBody[RegisterRequest](request) match {
        case Left(result) => Future.successful(result)
        case Right(registerRequest) =>
          authService.register(
            registerRequest.email,
            registerRequest.password,
            registerRequest.name
          ).map { authResponse =>
            Created(authResponse.asJson.noSpaces).as("application/json")
          }.recover {
            case e: ValidationException =>
              logger.warn(s"Registration failed: ${e.getMessage}")
              BadRequest(GenericError(e.getMessage).asJson.noSpaces).as("application/json")
            case e: Exception =>
              logger.error(s"Registration error: ${e.getMessage}", e)
              InternalServerError(GenericError("User registration failed").asJson.noSpaces).as("application/json")
          }
      }
    }
  }

  def forgotPassword(): Action[AnyContent] = Action.async { implicit request =>
    MetricUtils.withAPIMetrics("forgotPassword") {
      JsonParsers.parseJsonBody[ForgotPasswordRequest](request) match {
        case Left(result) => Future.successful(result)
        case Right(forgotRequest) =>
          authService.forgotPassword(forgotRequest.email).map { success =>
            if (success) {
              Ok(Map("message" -> "If the email exists, a password reset link has been sent").asJson.noSpaces).as("application/json")
            } else {
              InternalServerError(GenericError("Failed to process password reset").asJson.noSpaces).as("application/json")
            }
          }.recover {
            case e: Exception =>
              logger.error(s"Forgot password error: ${e.getMessage}", e)
              InternalServerError(Map("error" -> "Failed to process password reset").asJson.noSpaces).as("application/json")
          }
      }
    }
  }

  def resetPassword(): Action[AnyContent] = Action.async { implicit request =>
    MetricUtils.withAPIMetrics("resetPassword") {
      JsonParsers.parseJsonBody[ResetPasswordRequest](request) match {
        case Left(result) => Future.successful(result)
        case Right(resetRequest) =>
          authService.resetPassword(resetRequest.token, resetRequest.newPassword).map { success =>
            if (success) {
              Ok(Map("message" -> "Password reset successful").asJson.noSpaces).as("application/json")
            } else {
              InternalServerError(GenericError("Failed to reset password").asJson.noSpaces).as("application/json")
            }
          }.recover {
            case e: ValidationException =>
              logger.warn(s"Password reset failed: ${e.getMessage}")
              BadRequest(GenericError(e.getMessage).asJson.noSpaces).as("application/json")
            case e: Exception =>
              logger.error(s"Password reset error: ${e.getMessage}", e)
              InternalServerError(GenericError("Failed to reset password").asJson.noSpaces).as("application/json")
          }
      }
    }
  }

  def refreshAccessToken(): Action[AnyContent] = Action.async { implicit request =>
    MetricUtils.withAPIMetrics("refreshAccessToken") {
      request.body.asJson.flatMap(x => x.asJson.hcursor.get[String]("refreshToken").toOption) match {
        case Some(refreshToken) =>
          authService.refreshToken(refreshToken).map { authResponse =>
            Ok(authResponse.asJson.noSpaces).as("application/json")
          }.recover {
            case e: ValidationException =>
              logger.warn(s"Token refresh failed: ${e.getMessage}")
              Unauthorized(GenericError(e.getMessage).asJson.noSpaces).as("application/json")
            case e: Exception =>
              logger.error(s"Token refresh error: ${e.getMessage}", e)
              InternalServerError(GenericError("Token refresh failed").asJson.noSpaces).as("application/json")
          }
        case None =>
          Future.successful(BadRequest(GenericError("Refresh token required").asJson.noSpaces).as("application/json"))
      }
    }
  }
}
