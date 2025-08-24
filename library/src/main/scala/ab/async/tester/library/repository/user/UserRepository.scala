package ab.async.tester.library.repository.user

import ab.async.tester.domain.user.User
import com.google.inject.ImplementedBy

import scala.concurrent.Future

@ImplementedBy(classOf[UserRepositoryImpl])
trait UserRepository {

  def upsertUser(user: User): Future[User]

  def getUser(email: String): Future[Option[User]]

}
