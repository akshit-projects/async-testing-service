package ab.async.tester.library.repository.user

import ab.async.tester.domain.user.{User, UserRole}
import com.google.inject.ImplementedBy

import scala.concurrent.Future

@ImplementedBy(classOf[UserRepositoryImpl])
trait UserRepository {

  def upsertUser(user: User): Future[User]

  def getUser(email: String): Future[Option[User]]

  def getUserById(id: String): Future[Option[User]]

  def updateUserProfile(user: User): Future[User]

  def updateUserRole(userId: String, role: UserRole, isAdmin: Boolean): Future[Boolean]

}
