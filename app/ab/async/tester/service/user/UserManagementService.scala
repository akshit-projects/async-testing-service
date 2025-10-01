package ab.async.tester.service.user

import ab.async.tester.domain.common.PaginatedResponse
import ab.async.tester.domain.user.{UpdateUserRequest, User, UserRole}
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.repository.user.UserRepository
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UserManagementServiceImpl])
trait UserManagementService {
  def getAllUsers(search: Option[String], limit: Int, page: Int): Future[PaginatedResponse[User]]
  def getUserById(userId: String): Future[Option[User]]
  def updateUser(userId: String, updateRequest: UpdateUserRequest): Future[User]
}

@Singleton
class UserManagementServiceImpl @Inject()(
  userRepository: UserRepository
)(implicit ec: ExecutionContext) extends UserManagementService {

  implicit private val logger: Logger = Logger(this.getClass)
  private val serviceName = "UserManagementService"

  override def getAllUsers(search: Option[String], limit: Int, page: Int): Future[PaginatedResponse[User]] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "getAllUsers") {
      userRepository.findAll(search, limit, page).map { case (users, total) =>
        PaginatedResponse(
          data = users,
          pagination = ab.async.tester.domain.common.PaginationMetadata(
            page = page,
            limit = limit,
            total = total
          )
        )
      }.recover {
        case e: Exception =>
          logger.error(s"Error retrieving users: ${e.getMessage}", e)
          PaginatedResponse(
            data = List.empty,
            pagination = ab.async.tester.domain.common.PaginationMetadata(page, limit, 0)
          )
      }
    }
  }

  override def getUserById(userId: String): Future[Option[User]] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "getUserById") {
      userRepository.getUserById(userId)
    }
  }

  override def updateUser(userId: String, updateRequest: UpdateUserRequest): Future[User] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "updateUser") {
      userRepository.getUserById(userId).flatMap {
        case Some(user) =>
          // Validate and parse role if provided
          val roleOpt = updateRequest.role.map { roleStr =>
            try {
              UserRole.fromString(roleStr)
            } catch {
              case _: Exception =>
                throw ValidationException(s"Invalid role: $roleStr. Valid roles are: admin, user, viewer")
            }
          }

          // Update user metadata
          userRepository.updateUserMetadata(
            userId = userId,
            orgIds = updateRequest.orgIds,
            teamIds = updateRequest.teamIds,
            role = roleOpt,
            isAdmin = updateRequest.isAdmin,
            isActive = updateRequest.isActive
          ).flatMap { success =>
            if (success) {
              // Get updated user
              userRepository.getUserById(userId).map {
                case Some(updatedUser) =>
                  logger.info(s"User $userId updated successfully")
                  updatedUser
                case None =>
                  throw new IllegalStateException(s"User $userId not found after update")
              }
            } else {
              throw new IllegalStateException(s"Failed to update user $userId")
            }
          }

        case None =>
          throw ValidationException(s"User not found: $userId")
      }
    }
  }
}
