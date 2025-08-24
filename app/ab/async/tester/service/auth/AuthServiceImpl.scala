package ab.async.tester.service.auth

import ab.async.tester.domain.auth.GoogleClaims
import ab.async.tester.domain.requests.auth.LoginRequest
import ab.async.tester.domain.user.User
import ab.async.tester.exceptions.AuthExceptions.InvalidAuthException
import ab.async.tester.library.repository.user.UserRepository
import com.google.inject.{Inject, Singleton}
import pdi.jwt.{JwtAlgorithm, JwtJson, JwtOptions}
import play.api.{Configuration, Logger}
import play.api.libs.json.{Json, Reads}
import play.api.libs.ws.WSClient

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
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
                    upsertUser(validClaims)
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
            createdAt = Instant.now().toEpochMilli,
            modifiedAt = Instant.now().toEpochMilli,
            email = validClaims.email,
            name = validClaims.name,
            profilePicture = validClaims.picture,
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
    if (validClaims.name.isDefined) {
      updatedUser = updatedUser.copy(name = validClaims.name)
    }
    if (validClaims.picture.isDefined) {
      updatedUser = updatedUser.copy(profilePicture = validClaims.picture)
    }
    updatedUser.copy(modifiedAt = Instant.now().toEpochMilli)
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
