package ab.async.tester.service.auth

import ab.async.tester.domain.auth.GoogleClaims
import ab.async.tester.domain.requests.auth.{LoginRequest, UpdateProfileRequest, AdminUpdateUserRequest}
import ab.async.tester.domain.user.{User, UserRole}
import com.google.inject.ImplementedBy

import scala.concurrent.Future

@ImplementedBy(classOf[AuthServiceImpl])
trait AuthService {

  def loginUser(loginRequest: LoginRequest): Future[User]

  def updateUserProfile(userId: String, updateRequest: UpdateProfileRequest): Future[User]

  def getUserProfile(userId: String): Future[Option[User]]

  def adminUpdateUser(adminRequest: AdminUpdateUserRequest): Future[Boolean]

}
