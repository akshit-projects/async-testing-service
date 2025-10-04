package ab.async.tester.controllers

import ab.async.tester.controllers.auth.AuthenticatedAction
import ab.async.tester.domain.requests.auth.UpdateProfileRequest
import ab.async.tester.domain.response.GenericError
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.utils.{JsonParsers, MetricUtils}
import ab.async.tester.service.user.UserProfileService
import com.google.inject.{Inject, Singleton}
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import play.api.Logger
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserProfileController @Inject()(
  cc: ControllerComponents,
  userProfileService: UserProfileService,
  authenticatedAction: AuthenticatedAction
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  /**
   * Get current user profile
   * GET /api/v1/profile
   */
  def getUserProfile: Action[AnyContent] = authenticatedAction.async { implicit request =>
    MetricUtils.withAPIMetrics("getUserProfile") {
      userProfileService.getUserProfile(request.user.userId).map {
        case Some(profile) =>
          Ok(profile.asJson.noSpaces).as("application/json")
        case None =>
          NotFound(GenericError("User profile not found").asJson.noSpaces).as("application/json")
      }.recover {
        case ex: Exception =>
          logger.error(s"Failed to get profile: ${ex.getMessage}", ex)
          InternalServerError(GenericError("Failed to get profile").asJson.noSpaces).as("application/json")
      }
    }
  }

  /**
   * Update current user profile
   * PUT /api/v1/profile
   */
  def updateUserProfile(): Action[AnyContent] = authenticatedAction.async { implicit request =>
    MetricUtils.withAPIMetrics("updateUserProfile") {
      JsonParsers.parseJsonBody[UpdateProfileRequest](request)(implicitly, ec) match {
        case Left(result) => Future.successful(result)
        case Right(updateRequest) =>
          userProfileService.updateUserProfile(request.user.userId, updateRequest).map { updatedProfile =>
            Ok(updatedProfile.asJson.noSpaces).as("application/json")
          }.recover {
            case e: ValidationException =>
              logger.warn(s"Profile update failed: ${e.getMessage}")
              BadRequest(GenericError(e.getMessage).asJson.noSpaces).as("application/json")
            case ex: Exception =>
              logger.error(s"Failed to update profile: ${ex.getMessage}", ex)
              InternalServerError(GenericError("Failed to update profile").asJson.noSpaces).as("application/json")
          }
      }
    }
  }
}

