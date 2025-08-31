package ab.async.tester.service.auth

import ab.async.tester.domain.auth.GoogleClaims
import ab.async.tester.domain.requests.auth.{AdminUpdateUserRequest, LoginRequest, UpdateProfileRequest}
import ab.async.tester.domain.user.{User, UserRole}
import ab.async.tester.exceptions.AuthExceptions.InvalidAuthException
import ab.async.tester.library.repository.user.UserRepository
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
class AuthServiceImpl @Inject()(config: Configuration,
                                wsClient: WSClient,
                                userRepository: UserRepository)(implicit executionContext: ExecutionContext) extends AuthService {

  private val logger = Logger(this.getClass)
  implicit val reads: Reads[GoogleClaims] = Json.reads[GoogleClaims]
  private val GoogleCertsUrl = "https://www.googleapis.com/oauth2/v1/certs"
  private val GoogleIssuer1  = "accounts.google.com"
  private val GoogleIssuer2  = "https://accounts.google.com"
  private val clientId       = config.get[String]("google.clientId")

  override def loginUser(request: LoginRequest): Future[User] = {
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
                    upsertUser(validClaims).recover {
                      case ex: Exception =>
                        logger.error("Exception while upserting user", ex)
                        throw ex
                    }
                  case None              => throw InvalidAuthException() // TODO update exception type
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

  private def upsertUser(validClaims: GoogleClaims) = {
    for {
      existingUser <- userRepository.getUser(validClaims.email)
      updatedUser <- {
        if (existingUser.isDefined) {
          val updatedUser = getUpdatedUser(existingUser.get, validClaims)
          userRepository.upsertUser(updatedUser)
        } else {
          val newUser = User(
            id = UUID.randomUUID().toString,
            email = validClaims.email,
            name = validClaims.name,
            createdAt = Instant.now().toEpochMilli,
            modifiedAt = Instant.now().toEpochMilli,
            profilePicture = validClaims.picture,
            lastGoogleSync = Some(Instant.now().toEpochMilli)
          )
          userRepository.upsertUser(newUser)
        }
      }
    } yield {
      updatedUser
    }
  }

  private def getUpdatedUser(existingUser: User, validClaims: GoogleClaims) = {
    var updatedUser = existingUser

    // Only update fields that user hasn't manually modified
    if (validClaims.name.isDefined && !existingUser.isFieldUpdatedByUser("name")) {
      updatedUser = updatedUser.copy(name = validClaims.name)
    }
    if (validClaims.picture.isDefined && !existingUser.isFieldUpdatedByUser("profilePicture")) {
      updatedUser = updatedUser.copy(profilePicture = validClaims.picture)
    }

    updatedUser.copy(
      lastGoogleSync = Some(Instant.now().toEpochMilli),
      modifiedAt = Instant.now().toEpochMilli
    )
  }

  override def updateUserProfile(userId: String, updateRequest: UpdateProfileRequest): Future[User] = {
    userRepository.getUserById(userId).flatMap {
      case Some(existingUser) =>
        var updatedUser = existingUser
        var updatedFields = existingUser.userUpdatedFields


        updateRequest.phoneNumber.foreach { phoneNumber =>
          updatedUser = updatedUser.copy(phoneNumber = Some(phoneNumber))
          updatedUser.markFieldAsUpdatedByUser("phoneNumber")
        }
        updateRequest.name.foreach { name =>
          updatedUser = updatedUser.copy(name = Some(name))
          updatedUser.markFieldAsUpdatedByUser("name")
        }
        updateRequest.bio.foreach { bio =>
          updatedUser = updatedUser.copy(bio = Some(bio))
          updatedUser.markFieldAsUpdatedByUser("bio")
        }
        updateRequest.company.foreach { company =>
          updatedUser = updatedUser.copy(company = Some(company))
          updatedUser.markFieldAsUpdatedByUser("company")
        }

        val finalUser = updatedUser.copy(
          userUpdatedFields = updatedFields,
          modifiedAt = Instant.now().toEpochMilli
        )

        userRepository.updateUserProfile(finalUser)
      case None =>
        throw new RuntimeException(s"User with id $userId not found")
    }
  }

  override def getUserProfile(userId: String): Future[Option[User]] = {
    userRepository.getUserById(userId)
  }

  override def adminUpdateUser(adminRequest: AdminUpdateUserRequest): Future[Boolean] = {
    (adminRequest.role, adminRequest.isAdmin) match {
      case (Some(role), Some(isAdmin)) =>
        userRepository.updateUserRole(adminRequest.userId, role, isAdmin)
      case (Some(role), None) =>
        userRepository.updateUserRole(adminRequest.userId, role, role == UserRole.Admin)
      case (None, Some(isAdmin)) =>
        val role = if (isAdmin) UserRole.Admin else UserRole.User
        userRepository.updateUserRole(adminRequest.userId, role, isAdmin)
      case _ =>
        Future.successful(false)
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
}
