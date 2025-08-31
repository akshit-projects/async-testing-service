package ab.async.tester.controllers

import ab.async.tester.controllers.auth.{AuthenticatedAction, AuthorizedAction}
import ab.async.tester.domain.auth.ClientToken
import io.circe.generic.auto._
import ab.async.tester.domain.requests.auth.{AdminUpdateUserRequest, LoginRequest, UpdateProfileRequest}
import ab.async.tester.domain.response.GenericError
import ab.async.tester.domain.response.auth.UserProfileResponse
import ab.async.tester.library.utils.{JsonParsers, MetricUtils}
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
                                 authService: AuthService,
                                 authenticatedAction: AuthenticatedAction,
                                 authorizedAction: AuthorizedAction,
                                 config: play.api.Configuration
                               )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def loginUser: Action[AnyContent] = Action.async { implicit request =>
    MetricUtils.withAPIMetrics("loginUser") {
      JsonParsers.parseJsonBody[LoginRequest](request)(implicitly, ec) match {
        case Left(result) => Future.successful(result)
        case Right(authReq) =>
          authService.loginUser(authReq).map {
            user =>
              val expiry = Instant.now().plusSeconds(180000).getEpochSecond
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
  def getUserProfile: Action[AnyContent] = authenticatedAction.async { implicit request =>
    MetricUtils.withAPIMetrics("getUserProfile") {
      Future.successful(Ok(UserProfileResponse.fromUser(request.user).asJson.noSpaces))
    }
  }

  def updateUserProfile(): Action[AnyContent] = authenticatedAction.async { implicit request =>
    MetricUtils.withAPIMetrics("updateUserProfile") {
      JsonParsers.parseJsonBody[UpdateProfileRequest](request)(implicitly, ec) match {
        case Left(result) => Future.successful(result)
        case Right(updateRequest) =>
          authService.updateUserProfile(request.user.id, updateRequest).map { updatedUser =>
            Ok(UserProfileResponse.fromUser(updatedUser).asJson.noSpaces)
          }.recover {
            case ex: Exception =>
              InternalServerError(GenericError("Failed to update profile").asJson.noSpaces)
          }
      }
    }
  }

  /** PUT /api/v1/admin/users - Update user role/admin status (admin only) */
  def adminUpdateUser: Action[AnyContent] = authorizedAction.requireAdmin.async { implicit request =>
    MetricUtils.withAPIMetrics("adminUpdateUser") {
      JsonParsers.parseJsonBody[AdminUpdateUserRequest](request)(implicitly, ec) match {
        case Left(result) => Future.successful(result)
        case Right(adminRequest) =>
          authService.adminUpdateUser(adminRequest).map { success =>
            if (success) {
              Ok(Map("message" -> "User updated successfully").asJson.noSpaces)
            } else {
              BadRequest(GenericError("Failed to update user").asJson.noSpaces)
            }
          }.recover {
            case ex: Exception =>
              InternalServerError(GenericError("Failed to update user").asJson.noSpaces)
          }
      }
    }
  }


}
