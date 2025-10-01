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

  def findAll(search: Option[String], limit: Int, page: Int): Future[(List[User], Int)]

  def updateUserMetadata(userId: String, orgIds: Option[List[String]],
                         teamIds: Option[List[String]], role: Option[UserRole], isAdmin: Option[Boolean], isActive: Option[Boolean]): Future[Boolean]

}
