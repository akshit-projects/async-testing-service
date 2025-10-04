package ab.async.tester.library.auth

import com.google.inject.{Inject, Singleton}
import com.typesafe.config.Config
import io.circe.generic.auto._
import io.circe.syntax._
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

import java.time.Clock
import scala.util.{Failure, Success}

case class TokenPayload(
  userId: String,
  email: String,
  role: String,
  isAdmin: Boolean
)

@Singleton
class TokenGenerator @Inject()(config: Config) {
  private val secretKey = config.getString("auth.jwt.secret")
  private val tokenExpiryHours = config.getInt("auth.jwt.expiryHours")
  private val refreshTokenExpiryDays = config.getInt("auth.jwt.refreshTokenExpiryDays")

  /**
   * Generate an access token
   */
  def generateAccessToken(userId: String, email: String, role: String, isAdmin: Boolean): (String, Long) = {
    val expiresAt = System.currentTimeMillis() / 1000 + (tokenExpiryHours * 3600)
    
    val payload = TokenPayload(userId, email, role, isAdmin)
    val claim = JwtClaim(
      content = payload.asJson.noSpaces,
      expiration = Some(expiresAt),
      issuedAt = Some(System.currentTimeMillis() / 1000)
    )
    
    val token = JwtCirce.encode(claim, secretKey, JwtAlgorithm.HS256)
    (token, expiresAt * 1000) // Return milliseconds
  }

  /**
   * Generate a refresh token
   */
  def generateRefreshToken(userId: String): (String, Long) = {
    val expiresAt = System.currentTimeMillis() / 1000 + (refreshTokenExpiryDays * 24 * 3600)
    
    val claim = JwtClaim(
      content = s"""{"userId":"$userId","type":"refresh"}""",
      expiration = Some(expiresAt),
      issuedAt = Some(System.currentTimeMillis() / 1000)
    )
    
    val token = JwtCirce.encode(claim, secretKey, JwtAlgorithm.HS256)
    (token, expiresAt * 1000) // Return milliseconds
  }

  /**
   * Verify a refresh token
   */
  def verifyRefreshToken(token: String): Option[String] = {
    JwtCirce.decode(token, secretKey, Seq(JwtAlgorithm.HS256)) match {
      case Success(claim) =>
        io.circe.parser.decode[Map[String, String]](claim.content).toOption
          .flatMap(_.get("userId"))
      case Failure(_) =>
        None
    }
  }
}
