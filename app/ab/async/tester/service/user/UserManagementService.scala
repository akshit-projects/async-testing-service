package ab.async.tester.service.user

import ab.async.tester.domain.common.{PaginatedResponse, PaginationMetadata}
import ab.async.tester.domain.user.{DetailedUserProfile, UpdateUserRequest, UserRole}
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.repository.user.{UserAuthRepository, UserProfileRepository}
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UserManagementServiceImpl])
trait UserManagementService {
  def getAllUsers(search: Option[String], limit: Int, page: Int): Future[PaginatedResponse[DetailedUserProfile]]
  def getUserById(userId: String): Future[Option[DetailedUserProfile]]
  def updateUser(userId: String, updateRequest: UpdateUserRequest): Future[DetailedUserProfile]
}

@Singleton
class UserManagementServiceImpl @Inject()(
  userAuthRepository: UserAuthRepository,
  userProfileRepository: UserProfileRepository
)(implicit ec: ExecutionContext) extends UserManagementService {

  implicit private val logger: Logger = Logger(this.getClass)
  private val serviceName = "UserManagementService"

  override def getAllUsers(search: Option[String], limit: Int, page: Int): Future[PaginatedResponse[DetailedUserProfile]] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "getAllUsers") {
      // Use optimized JOIN query to get all data in a single DB call
      userProfileRepository.findAllWithAuth(search, limit, page).map { case (profilesWithAuth, total) =>
        val detailedProfiles = profilesWithAuth.map { case (profile, email, lastLoginAt) =>
          DetailedUserProfile(
            id = profile.id,
            email = email,
            name = profile.name,
            profilePicture = profile.profilePicture,
            company = profile.company,
            role = profile.role.name,
            isAdmin = profile.isAdmin,
            isActive = profile.isActive,
            orgIds = profile.orgIds,
            teamIds = profile.teamIds,
            lastLoginAt = lastLoginAt,
            createdAt = profile.createdAt,
            updatedAt = profile.updatedAt
          )
        }

        PaginatedResponse(
          data = detailedProfiles,
          pagination = PaginationMetadata(
            page = page,
            limit = limit,
            total = total
          )
        )
      }.recover {
        case e: Exception =>
          logger.error(s"Error retrieving users: ${e.getMessage}", e)
          PaginatedResponse(
            data = List[DetailedUserProfile](),
            pagination = PaginationMetadata(page, limit, 0)
          )
      }
    }
  }

  override def getUserById(userId: String): Future[Option[DetailedUserProfile]] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "getUserById") {
      val auth = userAuthRepository.findById(userId)
      val profile = userProfileRepository.findById(userId)
      (for {
        authOpt <- auth
        profileOpt <- profile
      } yield (authOpt, profileOpt)).map {
        case (Some(auth), Some(profile)) =>
          Some(DetailedUserProfile(
            id = profile.id,
            email = auth.email,
            name = profile.name,
            profilePicture = profile.profilePicture,
            company = profile.company,
            role = profile.role.name,
            isAdmin = profile.isAdmin,
            isActive = profile.isActive,
            orgIds = profile.orgIds,
            teamIds = profile.teamIds,
            lastLoginAt = auth.lastLoginAt,
            createdAt = profile.createdAt,
            updatedAt = profile.updatedAt
          ))
        case _ => None
      }
    }
  }

  override def updateUser(userId: String, updateRequest: UpdateUserRequest): Future[DetailedUserProfile] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "updateUser") {
      userProfileRepository.findById(userId).flatMap {
        case Some(_) =>
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
          userProfileRepository.updateMetadata(
            id = userId,
            orgIds = updateRequest.orgIds,
            teamIds = updateRequest.teamIds,
            role = roleOpt,
            isAdmin = updateRequest.isAdmin,
            isActive = updateRequest.isActive
          ).flatMap { success =>
            if (success) {
              // Get updated user
              getUserById(userId).map {
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
          Future.failed(ValidationException(s"User not found: $userId"))
      }
    }
  }
}
