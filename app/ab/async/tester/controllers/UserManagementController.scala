package ab.async.tester.controllers

import ab.async.tester.controllers.auth.AuthorizedAction
import ab.async.tester.domain.user.{Permissions, UpdateUserRequest}
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.utils.JsonParsers
import ab.async.tester.service.user.UserManagementService
import com.google.inject.{Inject, Singleton}
import io.circe.syntax._
import play.api.Logger
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserManagementController @Inject()(
  cc: ControllerComponents,
  authorizedAction: AuthorizedAction,
  userManagementService: UserManagementService
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  /**
   * Get all users with pagination and search
   * GET /api/v1/users?search=john&limit=20&page=0
   */
  def getAllUsers(search: Option[String], limit: Int, page: Int): Action[AnyContent] = 
    authorizedAction.requirePermission(Permissions.USERS_READ).async { implicit request =>
      logger.info(s"Getting all users - search: $search, limit: $limit, page: $page")
      
      userManagementService.getAllUsers(search, limit, page).map { response =>
        Ok(response.asJson.noSpaces).as("application/json")
      }.recover {
        case e: Exception =>
          logger.error(s"Error getting users: ${e.getMessage}", e)
          InternalServerError(s"Error retrieving users: ${e.getMessage}")
      }
    }

  /**
   * Get user by ID
   * GET /api/v1/users/:id
   */
  def getUserById(id: String): Action[AnyContent] = 
    authorizedAction.requirePermission(Permissions.USERS_READ).async { implicit request =>
      logger.info(s"Getting user by ID: $id")
      
      userManagementService.getUserById(id).map {
        case Some(user) =>
          Ok(user.asJson.noSpaces).as("application/json")
        case None =>
          NotFound(s"User not found: $id")
      }.recover {
        case e: Exception =>
          logger.error(s"Error getting user $id: ${e.getMessage}", e)
          InternalServerError(s"Error retrieving user: ${e.getMessage}")
      }
    }

  /**
   * Update user metadata (org, team, role, etc.)
   * PUT /api/v1/users/:id
   */
  def updateUser(id: String): Action[AnyContent] = 
    authorizedAction.requirePermission(Permissions.USERS_UPDATE).async { implicit request =>
      logger.info(s"Updating user: $id")
      
      JsonParsers.parseJsonBody[UpdateUserRequest](request) match {
        case Left(result) => Future.successful(result)
        case Right(updateRequest) =>
          userManagementService.updateUser(id, updateRequest).map { user =>
            Ok(user.asJson.noSpaces).as("application/json")
          }.recover {
            case e: ValidationException =>
              logger.warn(s"Validation error updating user $id: ${e.getMessage}")
              BadRequest(e.getMessage)
            case e: Exception =>
              logger.error(s"Error updating user $id: ${e.getMessage}", e)
              InternalServerError(s"Error updating user: ${e.getMessage}")
          }
      }
    }
}
