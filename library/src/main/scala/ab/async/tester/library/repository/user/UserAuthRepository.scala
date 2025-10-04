package ab.async.tester.library.repository.user

import ab.async.tester.domain.user.UserAuth
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Logger
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class UserAuthTable(tag: Tag) extends Table[UserAuth](tag, "user_auth") {
  def id = column[String]("id", O.PrimaryKey)
  def email = column[String]("email")
  def phoneNumber = column[Option[String]]("phone_number")
  def passwordHash = column[Option[String]]("password_hash")
  def authToken = column[Option[String]]("auth_token")
  def refreshToken = column[Option[String]]("refresh_token")
  def tokenExpiresAt = column[Option[Long]]("token_expires_at")
  def googleId = column[Option[String]]("google_id")
  def lastLoginAt = column[Option[Long]]("last_login_at")
  def passwordResetToken = column[Option[String]]("password_reset_token")
  def passwordResetExpiresAt = column[Option[Long]]("password_reset_expires_at")
  private def emailVerified = column[Boolean]("email_verified")
  def createdAt = column[Long]("created_at")
  def updatedAt = column[Long]("updated_at")

  def * = (id, email, phoneNumber, passwordHash, authToken, refreshToken, tokenExpiresAt, googleId, lastLoginAt, passwordResetToken, passwordResetExpiresAt, emailVerified, createdAt, updatedAt) <> (
    {
      case (id, email, phoneNumber, passwordHash, authToken, refreshToken, tokenExpiresAt, googleId, lastLoginAt, passwordResetToken, passwordResetExpiresAt, emailVerified, createdAt, updatedAt) =>
        UserAuth(id, email, phoneNumber, passwordHash, authToken, refreshToken, tokenExpiresAt, googleId, lastLoginAt, passwordResetToken, passwordResetExpiresAt, emailVerified, createdAt, updatedAt)
    },
    (u: UserAuth) => {
      Some((u.id, u.email, u.phoneNumber, u.passwordHash, u.authToken, u.refreshToken, u.tokenExpiresAt, u.googleId, u.lastLoginAt, u.passwordResetToken, u.passwordResetExpiresAt, u.emailVerified, u.createdAt, u.updatedAt))
    }
  )
}

@ImplementedBy(classOf[UserAuthRepositoryImpl])
trait UserAuthRepository {
  def findById(id: String): Future[Option[UserAuth]]
  def findByEmail(email: String): Future[Option[UserAuth]]
  def findByGoogleId(googleId: String): Future[Option[UserAuth]]
  def findByPasswordResetToken(token: String): Future[Option[UserAuth]]
  def insert(userAuth: UserAuth): Future[UserAuth]
  def update(userAuth: UserAuth): Future[Boolean]
  def updateAuthToken(id: String, authToken: String, refreshToken: String, expiresAt: Long): Future[Boolean]
  def updateAuthTokenAndLastLogin(id: String, authToken: String, refreshToken: String, expiresAt: Long): Future[Boolean]
  def updateLastLogin(id: String): Future[Boolean]
  def updatePasswordResetToken(email: String, token: String, expiresAt: Long): Future[Boolean]
  def updatePassword(id: String, passwordHash: String): Future[Boolean]
  def clearPasswordResetToken(id: String): Future[Boolean]
  def updatePasswordAndClearResetToken(id: String, passwordHash: String): Future[Boolean]
}

@Singleton
class UserAuthRepositoryImpl @Inject()(db: Database)(implicit ec: ExecutionContext) extends UserAuthRepository {

  private val repositoryName = "UserAuthRepository"
  private val userAuths = TableQuery[UserAuthTable]
  implicit private val logger: Logger = Logger(this.getClass)

  override def findById(id: String): Future[Option[UserAuth]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findById") {
      db.run(userAuths.filter(_.id === id).result.headOption)
    }
  }

  override def findByEmail(email: String): Future[Option[UserAuth]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findByEmail") {
      db.run(userAuths.filter(_.email === email).result.headOption)
    }
  }

  override def findByGoogleId(googleId: String): Future[Option[UserAuth]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findByGoogleId") {
      db.run(userAuths.filter(_.googleId === googleId).result.headOption)
    }
  }

  override def findByPasswordResetToken(token: String): Future[Option[UserAuth]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findByPasswordResetToken") {
      val now = Instant.now().toEpochMilli
      db.run(
        userAuths
          .filter(u => u.passwordResetToken === token && u.passwordResetExpiresAt > now)
          .result
          .headOption
      )
    }
  }

  override def insert(userAuth: UserAuth): Future[UserAuth] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "insert") {
      val now = Instant.now().toEpochMilli
      val authWithTimestamps = userAuth.copy(createdAt = now, updatedAt = now)
      db.run(userAuths += authWithTimestamps).map(_ => authWithTimestamps)
    }
  }

  override def update(userAuth: UserAuth): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "update") {
      val updatedAuth = userAuth.copy(updatedAt = Instant.now().toEpochMilli)
      db.run(userAuths.filter(_.id === userAuth.id).update(updatedAuth)).map(_ > 0)
    }
  }

  override def updateAuthToken(id: String, authToken: String, refreshToken: String, expiresAt: Long): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updateAuthToken") {
      val updateQuery = userAuths.filter(_.id === id)
        .map(u => (u.authToken, u.refreshToken, u.tokenExpiresAt, u.updatedAt))
        .update((Some(authToken), Some(refreshToken), Some(expiresAt), Instant.now().toEpochMilli))

      db.run(updateQuery).map(_ > 0)
    }
  }

  override def updateAuthTokenAndLastLogin(id: String, authToken: String, refreshToken: String, expiresAt: Long): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updateAuthTokenAndLastLogin") {
      val now = Instant.now().toEpochMilli
      val updateQuery = userAuths.filter(_.id === id)
        .map(u => (u.authToken, u.refreshToken, u.tokenExpiresAt, u.lastLoginAt, u.updatedAt))
        .update((Some(authToken), Some(refreshToken), Some(expiresAt), Some(now), now))

      db.run(updateQuery).map(_ > 0)
    }
  }

  override def updateLastLogin(id: String): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updateLastLogin") {
      val now = Instant.now().toEpochMilli
      val updateQuery = userAuths.filter(_.id === id)
        .map(u => (u.lastLoginAt, u.updatedAt))
        .update((Some(now), now))

      db.run(updateQuery).map(_ > 0)
    }
  }

  override def updatePasswordResetToken(email: String, token: String, expiresAt: Long): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updatePasswordResetToken") {
      val updateQuery = userAuths.filter(_.email === email)
        .map(u => (u.passwordResetToken, u.passwordResetExpiresAt, u.updatedAt))
        .update((Some(token), Some(expiresAt), Instant.now().toEpochMilli))
      
      db.run(updateQuery).map(_ > 0)
    }
  }

  override def updatePassword(id: String, passwordHash: String): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updatePassword") {
      val updateQuery = userAuths.filter(_.id === id)
        .map(u => (u.passwordHash, u.updatedAt))
        .update((Some(passwordHash), Instant.now().toEpochMilli))
      
      db.run(updateQuery).map(_ > 0)
    }
  }

  override def clearPasswordResetToken(id: String): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "clearPasswordResetToken") {
      val updateQuery = userAuths.filter(_.id === id)
        .map(u => (u.passwordResetToken, u.passwordResetExpiresAt, u.updatedAt))
        .update((None, None, Instant.now().toEpochMilli))

      db.run(updateQuery).map(_ > 0)
    }
  }

  override def updatePasswordAndClearResetToken(id: String, passwordHash: String): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updatePasswordAndClearResetToken") {
      val updateQuery = userAuths.filter(_.id === id)
        .map(u => (u.passwordHash, u.passwordResetToken, u.passwordResetExpiresAt, u.updatedAt))
        .update((Some(passwordHash), None, None, Instant.now().toEpochMilli))

      db.run(updateQuery).map(_ > 0)
    }
  }
}
