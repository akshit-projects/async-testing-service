package ab.async.tester.controllers

import controllers.BaseControllerSpec
import ab.async.tester.service.auth.AuthService
import ab.async.tester.domain.auth.{
  RegisterRequest,
  EmailLoginRequest,
  ForgotPasswordRequest
}
import ab.async.tester.domain.response.auth.{AuthResponse, UserInfo}
import ab.async.tester.exceptions.ValidationException
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import scala.concurrent.Future
import org.mockito.ArgumentMatchers.any

class AuthControllerSpec extends BaseControllerSpec {

  val mockAuthService = mock[AuthService]
  val cc = Helpers.stubControllerComponents()
  val controller = new AuthController(cc, mockAuthService)

  "AuthController" should {

    "register a new user successfully" in {
      val registerRequest =
        RegisterRequest("test@example.com", "password", Some("Test User"))
      val userInfo =
        UserInfo("userId", "test@example.com", Some("Test User"), "user", false)
      val authResponse = AuthResponse("token", "refreshToken", 3600L, userInfo)

      when(
        mockAuthService.register(any[String], any[String], any[Option[String]])
      )
        .thenReturn(Future.successful(authResponse))

      val request = FakeRequest(POST, "/api/v1/auth/register")
        .withJsonBody(Json.parse(registerRequest.asJson.noSpaces))

      val result: Future[Result] = controller.register().apply(request)

      status(result) mustBe CREATED
      contentAsJson(result) mustBe Json.parse(authResponse.asJson.noSpaces)
    }

    "return BadRequest when registration fails with ValidationException" in {
      val registerRequest =
        RegisterRequest("test@example.com", "password", Some("Test User"))
      when(
        mockAuthService.register(any[String], any[String], any[Option[String]])
      )
        .thenReturn(
          Future.failed(new ValidationException("Email already exists"))
        )

      val request = FakeRequest(POST, "/api/v1/auth/register")
        .withJsonBody(Json.parse(registerRequest.asJson.noSpaces))

      val result = controller.register().apply(request)

      status(result) mustBe BAD_REQUEST
    }

    "login with email successfully" in {
      val loginRequest = EmailLoginRequest("test@example.com", "password")
      val userInfo =
        UserInfo("userId", "test@example.com", Some("Test User"), "user", false)
      val authResponse = AuthResponse("token", "refreshToken", 3600L, userInfo)

      when(mockAuthService.loginWithEmail(any[String], any[String]))
        .thenReturn(Future.successful(authResponse))

      val request = FakeRequest(POST, "/api/v1/auth/login")
        .withJsonBody(Json.parse(loginRequest.asJson.noSpaces))

      val result = controller.loginWithEmail().apply(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.parse(authResponse.asJson.noSpaces)
    }

    "handle forgot password request" in {
      val forgotRequest = ForgotPasswordRequest("test@example.com")
      when(mockAuthService.forgotPassword(any[String]))
        .thenReturn(Future.successful(true))

      val request = FakeRequest(POST, "/api/v1/auth/forgot-password")
        .withJsonBody(Json.parse(forgotRequest.asJson.noSpaces))

      val result = controller.forgotPassword().apply(request)
      status(result) mustBe OK
    }
  }
}
