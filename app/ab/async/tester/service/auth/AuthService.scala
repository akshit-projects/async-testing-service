package ab.async.tester.service.auth

import ab.async.tester.domain.auth.GoogleClaims
import ab.async.tester.domain.requests.auth.LoginRequest
import ab.async.tester.domain.user.User
import com.google.inject.ImplementedBy

import scala.concurrent.Future

@ImplementedBy(classOf[AuthServiceImpl])
trait AuthService {

  def loginUser(loginRequest: LoginRequest): Future[User]

}
