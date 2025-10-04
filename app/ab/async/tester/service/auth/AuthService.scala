package ab.async.tester.service.auth

import ab.async.tester.domain.auth._
import ab.async.tester.domain.requests.auth.LoginRequest
import ab.async.tester.domain.response.auth.AuthResponse
import com.google.inject.ImplementedBy

import scala.concurrent.Future

@ImplementedBy(classOf[AuthServiceImpl])
trait AuthService {
  // Google OAuth login
  def loginWithGoogle(loginRequest: LoginRequest): Future[AuthResponse]

  // Email/password authentication
  def loginWithEmail(email: String, password: String): Future[AuthResponse]

  // User registration
  def register(email: String, password: String, name: Option[String]): Future[AuthResponse]

  // Password reset
  def forgotPassword(email: String): Future[Boolean]
  def resetPassword(token: String, newPassword: String): Future[Boolean]

  // Token refresh
  def refreshToken(refreshToken: String): Future[AuthResponse]
}
