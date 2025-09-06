package ab.async.tester.controllers.auth

import ab.async.tester.domain.response.GenericError
import ab.async.tester.domain.user.User
import ab.async.tester.library.repository.user.UserRepository
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.{Inject, Singleton}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import pdi.jwt.{Jwt, JwtAlgorithm}
import play.api.mvc._
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Request wrapper that includes authenticated user information
 */
case class AuthenticatedRequest[A](user: User, request: Request[A]) extends WrappedRequest[A](request)

/**
 * Authentication action that verifies JWT tokens and extracts user information
 */
@Singleton
class AuthenticatedAction @Inject()(
  parser: BodyParsers.Default,
  userRepository: UserRepository,
  config: Configuration
)(implicit ec: ExecutionContext) extends ActionBuilder[AuthenticatedRequest, AnyContent] {

  private val jwtSecret = config.get[String]("jwt.secret")
  private val logger = Logger(this.getClass)

  override def executionContext: ExecutionContext = ec
  override def parser: BodyParser[AnyContent] = parser

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    extractUserFromToken(request) match {
      case Some(tokenUser) =>
        // Always verify against database for fresh user data and security
        userRepository.getUserById(tokenUser.id).flatMap {
          case Some(currentUser) =>
            // Use current user data from DB, not stale token data
            block(AuthenticatedRequest(currentUser, request))
          case None =>
            // User no longer exists in database
            Future.successful(Results.Unauthorized(GenericError("User account not found").asJson.noSpaces))
        }.recover {
          case ex =>
            logger.error("Database error during authentication", ex)
            Results.InternalServerError(GenericError("Authentication service unavailable").asJson.noSpaces)
        }
      case None =>
        Future.successful(Results.Unauthorized(GenericError("Invalid or missing authentication token").asJson.noSpaces))
    }
  }

  private def extractUserFromToken[A](request: Request[A]): Option[User] = {
    MetricUtils.withAuthMetrics {
      for {
        authHeader <- request.headers.get("Authorization")
        token <- extractTokenFromHeader(authHeader)
        user <- validateTokenAndExtractUser(token)
      } yield user
    }
  }

  private def extractTokenFromHeader(authHeader: String): Option[String] = {
    if (authHeader.startsWith("Bearer ")) {
      Some(authHeader.substring(7))
    } else {
      None
    }
  }

  private def validateTokenAndExtractUser(token: String): Option[User] = {
    Try {
      Jwt.decode(token, jwtSecret, Seq(JwtAlgorithm.HS256)).toOption
    } match {
      case Success(claim) =>
        if (claim.isEmpty) return None
        decode[User](claim.get.content) match {
          case Right(user) => Some(user)
          case Left(_) => None
        }
      case Failure(_) => None
    }
  }
}

/**
 * Companion object for creating authenticated actions
 */
object AuthenticatedAction {
  def apply(authenticatedAction: AuthenticatedAction): AuthenticatedAction = authenticatedAction
}
