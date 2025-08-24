package ab.async.tester.library.repository.user

import ab.async.tester.domain.user.User
import com.google.inject.{Inject, Singleton}
import play.api.Logger
import slick.jdbc.H2Profile.Table
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}


class UserTable(tag: Tag) extends Table[User](tag, "users") {

  def id          = column[String]("id", O.PrimaryKey)
  def email       = column[String]("email")
  def createdAt   = column[Long]("created_at")
  def modifiedAt  = column[Long]("modified_at")
  def name        = column[Option[String]]("name")
  def profilePicture  = column[Option[String]]("profile_picture")

  def * = (id, email, name, profilePicture, createdAt, modifiedAt) <> (
    {
      case (id, email, name, profilePicture, createdAt, modifiedAt) =>
        //          val stepsObj = decode[List[FlowStep]](steps).getOrElse(Nil)
        User(id, email, name, profilePicture, createdAt, modifiedAt)
    },
    (u: User) => {
      Some((u.id, u.email, u.name, u.profilePicture, u.createdAt, u.modifiedAt))
    }
  )
}

@Singleton
class UserRepositoryImpl @Inject()(db: Database)(implicit executionContext: ExecutionContext) extends UserRepository {


  private implicit val logger: Logger = Logger(this.getClass)
  private val repositoryName = "UserRepository"

  private val users = TableQuery[UserTable]

  override def upsertUser(user: User): Future[User] = {
    val updatedUser = user.copy(modifiedAt = Instant.now().toEpochMilli)
    db.run(users.insertOrUpdate(updatedUser)).map(_ => updatedUser)
  }

  override def getUser(email: String): Future[Option[User]] = {
    db.run(users.filter(_.email === email).result.headOption)
  }
}
