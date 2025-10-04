package ab.async.tester.service.auth

import ab.async.tester.domain.auth._
import ab.async.tester.domain.requests.auth.LoginRequest
import ab.async.tester.domain.response.auth.{AuthResponse, UserInfo}
import ab.async.tester.domain.user.{UserAuth, UserProfile, UserRole}
import ab.async.tester.exceptions.AuthExceptions.InvalidAuthException
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.auth.{PasswordHasher, TokenGenerator}
import ab.async.tester.library.repository.user.{UserAuthRepository, UserProfileRepository}
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.{Inject, Singleton}
import pdi.jwt.{JwtAlgorithm, JwtJson, JwtOptions}
import play.api.libs.json.{Json, Reads}
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import java.io.ByteArrayInputStream
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.time.Instant
import java.util.{Base64, UUID}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class AuthServiceImpl @Inject()(
  config: Configuration,
  wsClient: WSClient,
  userAuthRepository: UserAuthRepository,
  userProfileRepository: UserProfileRepository,
  tokenGenerator: TokenGenerator,
  emailService: EmailService
)(implicit ec: ExecutionContext) extends AuthService {

  private implicit val logger: Logger = Logger(this.getClass)
  private val serviceName = "AuthService"
  private val resetLinkPrefix = "http://localhost:3000/reset-password?token="
  implicit val reads: Reads[GoogleClaims] = Json.reads[GoogleClaims]
  private val GoogleCertsUrl = "https://www.googleapis.com/oauth2/v1/certs"
  private val GoogleIssuer1  = "accounts.google.com"
  private val GoogleIssuer2  = "https://accounts.google.com"
  private val clientId       = config.get[String]("google.clientId")

  override def loginWithGoogle(request: LoginRequest): Future[AuthResponse] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "loginWithGoogle") {
      val maybeKid: Option[String] =
        JwtJson.decodeJsonAll(request.idToken, JwtOptions(signature = false)) match {
          case Success((headerJs, _, _)) => (headerJs \ "kid").asOpt[String]
          case Failure(_)                => None
        }

      maybeKid match {
        case Some(kid) =>
          getGooglePublicKey(kid).flatMap {
            case Some(pubKey) =>
              JwtJson.decodeJson(request.idToken, pubKey, Seq(JwtAlgorithm.RS256)) match {
                case Success(claimJs) =>
                  claimJs.validate[GoogleClaims].asOpt.filter { claims =>
                    (claims.iss == GoogleIssuer1 || claims.iss == GoogleIssuer2) &&
                      claims.aud == clientId &&
                      claims.exp > Instant.now.getEpochSecond
                  } match {
                    case Some(validClaims) =>
                      upsertUserFromGoogle(validClaims).recover {
                        case ex: Exception =>
                          logger.error("Exception while upserting user", ex)
                          throw ex
                      }
                    case None => throw InvalidAuthException()
                  }

                case Failure(_) =>
                  throw InvalidAuthException()
              }

            case None =>
              throw InvalidAuthException()
          }

        case None => throw InvalidAuthException()
      }
    }
  }

  override def loginWithEmail(email: String, password: String): Future[AuthResponse] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "loginWithEmail") {
      for {
        authOpt <- userAuthRepository.findByEmail(email)
        auth <- authOpt match {
          case Some(a) => Future.successful(a)
          case None => Future.failed(ValidationException("Invalid email or password"))
        }

        // Verify password
        _ <- if (auth.passwordHash.exists(hash => PasswordHasher.verifyPassword(password, hash))) {
          Future.successful(())
        } else {
          Future.failed(ValidationException("Invalid email or password"))
        }

        // Get user profile
        profileOpt <- userProfileRepository.findById(auth.id)
        profile <- profileOpt match {
          case Some(p) => Future.successful(p)
          case None => Future.failed(new IllegalStateException("User profile not found"))
        }

        // Check if user is active
        _ <- if (profile.isActive) {
          Future.successful(())
        } else {
          Future.failed(ValidationException("User account is inactive"))
        }

        // Generate tokens
        (accessToken, expiresAt) = tokenGenerator.generateAccessToken(
          auth.id, auth.email, profile.role.name, profile.isAdmin
        )
        (refreshToken, _) = tokenGenerator.generateRefreshToken(auth.id)

        // Update auth tokens and last login in a single DB operation
        _ <- userAuthRepository.updateAuthTokenAndLastLogin(auth.id, accessToken, refreshToken, expiresAt)

      } yield {
        AuthResponse(
          token = accessToken,
          refreshToken = refreshToken,
          expiresAt = expiresAt,
          user = UserInfo(
            id = auth.id,
            email = auth.email,
            name = profile.name,
            role = profile.role.name,
            isAdmin = profile.isAdmin,
            orgIds = profile.orgIds,
            teamIds = profile.teamIds
          )
        )
      }
    }
  }

  override def register(email: String, password: String, name: Option[String]): Future[AuthResponse] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "registerUser") {
      // Validate email format
      if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
        return Future.failed(ValidationException("Invalid email format"))
      }

      // Validate password strength
      if (password.length < 8) {
        return Future.failed(ValidationException("Password must be at least 8 characters long"))
      }

      for {
        // Check if user already exists
        existingUser <- userAuthRepository.findByEmail(email)
        _ <- existingUser match {
          case Some(_) => Future.failed(ValidationException("User with this email already exists"))
          case None => Future.successful(())
        }

        // Create user ID
        userId = UUID.randomUUID().toString

        // Hash password
        passwordHash = PasswordHasher.hashPassword(password)

        (userProfile, _) <- registerUser(userId, email, name, passwordHash)

        // Generate tokens
        (accessToken, expiresAt) = tokenGenerator.generateAccessToken(
          userId, email, userProfile.role.name, userProfile.isAdmin
        )
        (refreshToken, _) = tokenGenerator.generateRefreshToken(userId)

        // Update auth tokens
        _ <- userAuthRepository.updateAuthToken(userId, accessToken, refreshToken, expiresAt)
      } yield {
        AuthResponse(
          token = accessToken,
          refreshToken = refreshToken,
          expiresAt = expiresAt,
          user = UserInfo(
            id = userId,
            email = email,
            name = name,
            role = userProfile.role.name,
            isAdmin = userProfile.isAdmin,
            orgIds = None,
            teamIds = None
          )
        )
      }
    }
  }

  private def registerUser(userId: String, email: String, name: Option[String], passwordHash: String) = {
    val userAuth = {
      UserAuth(
        id = userId,
        email = email,
        phoneNumber = None,
        passwordHash = Some(passwordHash),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
      )
    }
    val userProfile = {
      UserProfile(
        id = userId,
        name = name,
        role = UserRole.User,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
      )
    }

    for {
      _ <- userAuthRepository.insert(userAuth)
      _ <- userProfileRepository.insert(userProfile)
    } yield {
      (userProfile, userAuth)
    }
  }

  override def forgotPassword(email: String): Future[Boolean] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "forgotPassword") {
      for {
        authOpt <- userAuthRepository.findByEmail(email)
        result <- authOpt match {
          case Some(_) =>
            // Generate reset token
            val resetToken = PasswordHasher.generateResetToken()
            val expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 hours

            // Update user with reset token
            userAuthRepository.updatePasswordResetToken(email, resetToken, expiresAt).map { success =>
              if (success) {
                val resetLink = resetLinkPrefix + resetToken
                try {
                  emailService.sendEmail(email, "Reset Link", resetLink)
                } catch {
                  case ex: Exception =>
                    logger.error("Unable to send reset link to user")
                    throw ex
                }
                logger.info(s"Password reset requested for: $email. Token: $resetToken")
                true
              } else {
                false
              }
            }
          case None =>
            logger.warn(s"Password reset requested for non-existent email: $email")
            Future.successful(true)
        }
      } yield result
    }
  }

  override def resetPassword(token: String, newPassword: String): Future[Boolean] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "resetPassword") {
      // Validate password strength
      if (newPassword.length < 8) {
        return Future.failed(ValidationException("Password must be at least 8 characters long"))
      }

      for {
        authOpt <- userAuthRepository.findByPasswordResetToken(token)
        auth <- authOpt match {
          case Some(a) => Future.successful(a)
          case None => Future.failed(ValidationException("Invalid or expired reset token"))
        }

        // Hash new password
        passwordHash = PasswordHasher.hashPassword(newPassword)

        // Update password and clear reset token in a single DB operation
        success <- userAuthRepository.updatePasswordAndClearResetToken(auth.id, passwordHash)

        _ = if (success) logger.info(s"Password reset successful for user: ${auth.id}")

      } yield success
    }
  }

  override def refreshToken(refreshToken: String): Future[AuthResponse] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "refreshToken") {
      for {
        userIdOpt <- Future.successful(tokenGenerator.verifyRefreshToken(refreshToken))
        userId <- userIdOpt match {
          case Some(id) => Future.successful(id)
          case None => Future.failed(ValidationException("Invalid refresh token"))
        }
        // Get user auth and profile
        authOpt <- userAuthRepository.findById(userId)
        auth <- authOpt match {
          case Some(a) => Future.successful(a)
          case None => Future.failed(ValidationException("User not found"))
        }
        profileOpt <- userProfileRepository.findById(userId)
        profile <- profileOpt match {
          case Some(p) => Future.successful(p)
          case None => Future.failed(new IllegalStateException("User profile not found"))
        }
        // Check if user is active
        _ <- if (profile.isActive) {
          Future.successful(())
        } else {
          Future.failed(ValidationException("User account is inactive"))
        }
        // Generate new tokens
        (accessToken, expiresAt) = tokenGenerator.generateAccessToken(
          userId, auth.email, profile.role.name, profile.isAdmin
        )
        (newRefreshToken, _) = tokenGenerator.generateRefreshToken(userId)

        // Update auth tokens
        _ <- userAuthRepository.updateAuthToken(userId, accessToken, newRefreshToken, expiresAt)

      } yield AuthResponse(
        token = accessToken,
        refreshToken = newRefreshToken,
        expiresAt = expiresAt,
        user = UserInfo(
          id = userId,
          email = auth.email,
          name = profile.name,
          role = profile.role.name,
          isAdmin = profile.isAdmin,
          orgIds = profile.orgIds,
          teamIds = profile.teamIds
        )
      )
    }
  }

  /** Fetch Google certs and return the public key for the given kid */
  private def getGooglePublicKey(kid: String): Future[Option[PublicKey]] = {
    wsClient.url(GoogleCertsUrl).get().map { resp =>
      val certs = resp.json.as[Map[String, String]]
      val response = certs.get(kid).map(parseRsaPublicKeyFromPem)
      response.flatMap(_.toOption)
    }.recover {
      case ex: Exception =>
        logger.error("Unable to get google public key", ex)
        throw ex
    }
  }

  /** Convert PEM cert → RSA PublicKey */
  private def parseRsaPublicKeyFromPem(pem: String): Try[PublicKey] = Try {
    val cleanPem = pem
      .replace("-----BEGIN CERTIFICATE-----", "")
      .replace("-----END CERTIFICATE-----", "")
      .replaceAll("\\s", "")

    val decoded = Base64.getDecoder.decode(cleanPem)
    val certFactory = CertificateFactory.getInstance("X.509")
    val cert = certFactory.generateCertificate(new ByteArrayInputStream(decoded))
    cert.getPublicKey
  }

  private def registerGoogleUser(validClaims: GoogleClaims) = {
    // New user - create both auth and profile
    val userId = UUID.randomUUID().toString
    val now = Instant.now().toEpochMilli

    val userAuth = UserAuth(
      id = userId,
      email = validClaims.email,
      googleId = Some(validClaims.sub),
      emailVerified = validClaims.email_verified.getOrElse(false),
      createdAt = now,
      updatedAt = now
    )

    val userProfile = UserProfile(
      id = userId,
      name = validClaims.name,
      profilePicture = validClaims.picture,
      role = UserRole.User,
      lastGoogleSync = Some(now),
      createdAt = now,
      updatedAt = now
    )

    val userAuthFuture = userAuthRepository.insert(userAuth)
    val userProfileFuture = userProfileRepository.insert(userProfile)

    for {
      _ <- userAuthFuture
      _ <- userProfileFuture

      // Generate tokens
      (accessToken, expiresAt) = tokenGenerator.generateAccessToken(
        userId, validClaims.email, userProfile.role.name, userProfile.isAdmin
      )
      (refreshToken, _) = tokenGenerator.generateRefreshToken(userId)

      // Update auth tokens and last login
      _ <- userAuthRepository.updateAuthTokenAndLastLogin(userId, accessToken, refreshToken, expiresAt)
    } yield {
      logger.info(s"New user registered via Google: ${validClaims.email}")
      AuthResponse(
        token = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAt,
        user = UserInfo(
          id = userId,
          email = validClaims.email,
          name = validClaims.name,
          role = userProfile.role.name,
          isAdmin = userProfile.isAdmin,
          orgIds = None,
          teamIds = None
        )
      )
    }
  }

  private def updateExistingGoogleUser(existingAuth: UserAuth, validClaims: GoogleClaims) = {
    for {
      profileOpt <- userProfileRepository.findById(existingAuth.id)
      profile <- profileOpt match {
        case Some(p) => Future.successful(p)
        case None => Future.failed(new IllegalStateException("User profile not found"))
      }

      // Update profile with Google data if fields haven't been manually updated
      updatedProfile = {
        var updated = profile
        if (validClaims.name.isDefined && !profile.userUpdatedFields.contains("name")) {
          updated = updated.copy(name = validClaims.name)
        }
        if (validClaims.picture.isDefined && !profile.userUpdatedFields.contains("profilePicture")) {
          updated = updated.copy(profilePicture = validClaims.picture)
        }
        updated.copy(
          lastGoogleSync = Some(Instant.now().toEpochMilli),
          updatedAt = Instant.now().toEpochMilli
        )
      }

      _ <- if (updatedProfile != profile) {
        userProfileRepository.update(updatedProfile)
      } else {
        Future.successful(true)
      }

      // Generate tokens
      (accessToken, expiresAt) = tokenGenerator.generateAccessToken(
        existingAuth.id, existingAuth.email, updatedProfile.role.name, updatedProfile.isAdmin
      )
      (refreshToken, _) = tokenGenerator.generateRefreshToken(existingAuth.id)

      // Update auth tokens and last login
      _ <- userAuthRepository.updateAuthTokenAndLastLogin(existingAuth.id, accessToken, refreshToken, expiresAt)

    } yield {
      AuthResponse(
        token = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAt,
        user = UserInfo(
          id = existingAuth.id,
          email = existingAuth.email,
          name = updatedProfile.name,
          role = updatedProfile.role.name,
          isAdmin = updatedProfile.isAdmin,
          orgIds = updatedProfile.orgIds,
          teamIds = updatedProfile.teamIds
        )
      )
    }
  }

  private def upsertUserFromGoogle(validClaims: GoogleClaims): Future[AuthResponse] = {
    for {
      // Check if user exists
      existingAuthOpt <- userAuthRepository.findByEmail(validClaims.email)
      result <- existingAuthOpt match {
        case Some(existingAuth) =>
          // User exists - update profile if needed
          updateExistingGoogleUser(existingAuth, validClaims)
        case None =>
          registerGoogleUser(validClaims)
      }
    } yield result
  }

}
