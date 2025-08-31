package ab.async.tester.controllers.auth

import ab.async.tester.domain.response.GenericError
import ab.async.tester.domain.user.{User, UserRole}
import com.google.inject.{Inject, Singleton}
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

/**
 * Authorization action that checks user permissions for specific operations
 */
@Singleton
class AuthorizedAction @Inject()(
  authenticatedAction: AuthenticatedAction
)(implicit ec: ExecutionContext) {

  /**
   * Creates an action that requires specific permissions
   */
  def requirePermission(permission: String): ActionBuilder[AuthenticatedRequest, AnyContent] = {
    new ActionBuilder[AuthenticatedRequest, AnyContent] {
      override def executionContext: ExecutionContext = ec
      override def parser: BodyParser[AnyContent] = authenticatedAction.parser

      override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
        authenticatedAction.invokeBlock(request, (authRequest: AuthenticatedRequest[A]) =>
          if (hasPermission(authRequest.user, permission)) {
            block(authRequest)
          } else {
            Future.successful(Results.Forbidden(GenericError("Insufficient permissions").asJson.noSpaces))
          })
      }
    }
  }

  /**
   * Creates an action that requires admin role
   */
  def requireAdmin: ActionBuilder[AuthenticatedRequest, AnyContent] = {
    new ActionBuilder[AuthenticatedRequest, AnyContent] {
      override def executionContext: ExecutionContext = ec
      override def parser: BodyParser[AnyContent] = authenticatedAction.parser

      override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
        authenticatedAction.invokeBlock(request, (authRequest: AuthenticatedRequest[A]) =>
          if (authRequest.user.isAdmin || authRequest.user.role == UserRole.Admin) {
            block(authRequest)
          } else {
            Future.successful(Results.Forbidden(GenericError("Admin access required").asJson.noSpaces))
          })
      }
    }
  }

  /**
   * Check if user has specific permission
   */
  private def hasPermission(user: User, permission: String): Boolean = {
    user.role.permissions.contains(permission) || user.isAdmin
  }
}
