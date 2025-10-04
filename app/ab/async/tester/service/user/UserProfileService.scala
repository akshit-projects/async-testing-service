package ab.async.tester.service.user

import ab.async.tester.domain.requests.auth.UpdateProfileRequest
import ab.async.tester.domain.user.{PublicUserProfile, UserProfile}
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.repository.user.{UserAuthRepository, UserProfileRepository}
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Logger

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UserProfileServiceImpl])
trait UserProfileService {
  def getUserProfile(userId: String): Future[Option[PublicUserProfile]]
  def updateUserProfile(userId: String, updateRequest: UpdateProfileRequest): Future[PublicUserProfile]
}

@Singleton
class UserProfileServiceImpl @Inject()(
  userAuthRepository: UserAuthRepository,
  userProfileRepository: UserProfileRepository
)(implicit ec: ExecutionContext) extends UserProfileService {

  private implicit val logger: Logger = Logger(this.getClass)
  private val serviceName = "UserProfileService"

  override def getUserProfile(userId: String): Future[Option[PublicUserProfile]] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "getUserProfile") {
      userProfileRepository.findById(userId).map {
        case Some(profile) =>
          Some(PublicUserProfile(
            id = profile.id,
            name = profile.name,
            profilePicture = profile.profilePicture,
            company = profile.company,
            role = profile.role.name,
            isAdmin = profile.isAdmin,
            orgIds = profile.orgIds,
            teamIds = profile.teamIds,
            createdAt = profile.createdAt,
            updatedAt = profile.updatedAt
          ))
        case None => None
      }
    }
  }

  override def updateUserProfile(userId: String, updateRequest: UpdateProfileRequest): Future[PublicUserProfile] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "updateUserProfile") {
      for {
        // Get existing profile
        profileOpt <- userProfileRepository.findById(userId)
        (updatedProfile, updatedUserFields) = {
          val existingProfile = profileOpt match {
            case Some(p) => p
            case None => throw ValidationException(s"User profile not found: $userId")
          }
          // Update profile fields
          var updatedProfile = existingProfile
          var updatedFields = existingProfile.userUpdatedFields

          updateRequest.name.foreach { name =>
            updatedProfile = updatedProfile.copy(name = Some(name))
            updatedFields = updatedFields + "name"
          }
          updateRequest.bio.foreach { bio =>
            updatedProfile = updatedProfile.copy(bio = Some(bio))
            updatedFields = updatedFields + "bio"
          }
          updateRequest.company.foreach { company =>
            updatedProfile = updatedProfile.copy(company = Some(company))
            updatedFields = updatedFields + "company"
          }
          (updatedProfile, updatedFields)
        }
        
        // Update phone number in auth table if provided
        _ <- updateRequest.phoneNumber match {
          case Some(phoneNumber) =>
            userAuthRepository.findById(userId).flatMap {
              case Some(auth) =>
                val updatedAuth = auth.copy(
                  phoneNumber = Some(phoneNumber),
                  updatedAt = Instant.now().toEpochMilli
                )
                userAuthRepository.update(updatedAuth)
              case None =>
                Future.failed(ValidationException(s"User auth not found: $userId"))
            }
          case None => Future.successful(true)
        }
        
        // Save updated profile
        finalProfile = updatedProfile.copy(
          userUpdatedFields = updatedUserFields,
          updatedAt = Instant.now().toEpochMilli
        )
        
        _ <- userProfileRepository.update(finalProfile)
        
        _ = logger.info(s"User profile updated: $userId")
        
      } yield PublicUserProfile(
        id = finalProfile.id,
        name = finalProfile.name,
        profilePicture = finalProfile.profilePicture,
        company = finalProfile.company,
        role = finalProfile.role.name,
        isAdmin = finalProfile.isAdmin,
        orgIds = finalProfile.orgIds,
        teamIds = finalProfile.teamIds,
        createdAt = finalProfile.createdAt,
        updatedAt = finalProfile.updatedAt
      )
    }
  }
}

