package ab.async.tester.controllers

import ab.async.tester.controllers.auth.{AuthenticatedAction, AuthorizedAction}
import ab.async.tester.domain.requests.auth.UpdateProfileRequest
import ab.async.tester.domain.response.GenericError
import ab.async.tester.domain.user.{Permissions, UpdateUserRequest}
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.utils.{JsonParsers, MetricUtils}
import ab.async.tester.service.user.{UserManagementService, UserProfileService}
import com.google.inject.{Inject, Singleton}
import io.circe.syntax._
import io.circe.generic.auto._
import play.api.Logger
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

/** Unified controller for user management and profile operations Combines admin
  * user management and authenticated user profile endpoints
  */
@Singleton
class UserManagementController @Inject() (
    cc: ControllerComponents,
    authenticatedAction: AuthenticatedAction,
    authorizedAction: AuthorizedAction,
    userManagementService: UserManagementService,
    userProfileService: UserProfileService
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  // ========================================
  // User Profile Endpoints (Authenticated)
  // ========================================

  /** Get current user profile GET /api/v1/profile
    */
  def getCurrentUserProfile: Action[AnyContent] = authenticatedAction.async {
    implicit request =>
      MetricUtils.withAPIMetrics("getCurrentUserProfile") {
        userProfileService
          .getUserProfile(request.user.userId)
          .map {
            case Some(profile) =>
              Ok(profile.asJson.noSpaces).as("application/json")
            case None =>
              NotFound(GenericError("User profile not found").asJson.noSpaces)
                .as("application/json")
          }
          .recover { case ex: Exception =>
            logger.error(s"Failed to get profile: ${ex.getMessage}", ex)
            InternalServerError(
              GenericError("Failed to get profile").asJson.noSpaces
            ).as("application/json")
          }
      }
  }

  /** Update current user profile PUT /api/v1/profile
    */
  def updateCurrentUserProfile(): Action[AnyContent] =
    authenticatedAction.async { implicit request =>
      MetricUtils.withAPIMetrics("updateCurrentUserProfile") {
        JsonParsers
          .parseJsonBody[UpdateProfileRequest](request)(implicitly, ec) match {
          case Left(result)         => Future.successful(result)
          case Right(updateRequest) =>
            userProfileService
              .updateUserProfile(request.user.userId, updateRequest)
              .map { updatedProfile =>
                Ok(updatedProfile.asJson.noSpaces).as("application/json")
              }
              .recover {
                case e: ValidationException =>
                  logger.warn(s"Profile update failed: ${e.getMessage}")
                  BadRequest(GenericError(e.getMessage).asJson.noSpaces)
                    .as("application/json")
                case ex: Exception =>
                  logger.error(
                    s"Failed to update profile: ${ex.getMessage}",
                    ex
                  )
                  InternalServerError(
                    GenericError("Failed to update profile").asJson.noSpaces
                  ).as("application/json")
              }
        }
      }
    }

  // ========================================
  // User Management Endpoints (Admin)
  // ========================================

  /** Get all users (admin only) GET /api/v1/users
    */
  def getAllUsers(
      search: Option[String],
      limit: Int,
      page: Int
  ): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.USERS_READ).async {
      implicit request =>
        MetricUtils.withAPIMetrics("getAllUsers") {
          logger.info(
            s"Getting all users - search: $search, limit: $limit, page: $page"
          )

          userManagementService
            .getAllUsers(search, limit, page)
            .map { response =>
              Ok(response.asJson.noSpaces).as("application/json")
            }
            .recover { case e: Exception =>
              logger.error(s"Error getting users: ${e.getMessage}", e)
              InternalServerError(s"Error retrieving users: ${e.getMessage}")
            }
        }
    }

  /** Get user by ID (admin only) GET /api/v1/users/:id
    */
  def getUserById(id: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.USERS_READ).async {
      implicit request =>
        MetricUtils.withAPIMetrics("getUserById") {
          logger.info(s"Getting user by ID: $id")

          userManagementService
            .getUserById(id)
            .map {
              case Some(user) =>
                Ok(user.asJson.noSpaces).as("application/json")
              case None =>
                NotFound(s"User not found: $id")
            }
            .recover { case e: Exception =>
              logger.error(s"Error getting user $id: ${e.getMessage}", e)
              InternalServerError(s"Error retrieving user: ${e.getMessage}")
            }
        }
    }

  /** Update user (admin only) PUT /api/v1/users/:id
    */
  def updateUser(id: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.USERS_UPDATE).async {
      implicit request =>
        MetricUtils.withAPIMetrics("updateUser") {
          logger.info(s"Updating user: $id")

          JsonParsers.parseJsonBody[UpdateUserRequest](request) match {
            case Left(result)         => Future.successful(result)
            case Right(updateRequest) =>
              userManagementService
                .updateUser(id, updateRequest)
                .map { user =>
                  Ok(user.asJson.noSpaces).as("application/json")
                }
                .recover {
                  case e: ValidationException =>
                    logger.warn(
                      s"Validation error updating user $id: ${e.getMessage}"
                    )
                    BadRequest(e.getMessage)
                  case e: Exception =>
                    logger.error(s"Error updating user $id: ${e.getMessage}", e)
                    InternalServerError(s"Error updating user: ${e.getMessage}")
                }
          }
        }
    }
}
