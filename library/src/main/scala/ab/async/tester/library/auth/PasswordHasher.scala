package ab.async.tester.library.auth

import org.mindrot.jbcrypt.BCrypt

/**
 * Utility for hashing and verifying passwords using BCrypt
 */
object PasswordHasher {

  /**
   * Hash a password using BCrypt
   */
  def hashPassword(password: String): String = {
    BCrypt.hashpw(password, BCrypt.gensalt(12))
  }

  /**
   * Verify a password against a hash
   */
  def verifyPassword(password: String, hash: String): Boolean = {
    try {
      BCrypt.checkpw(password, hash)
    } catch {
      case _: Exception => false
    }
  }

  /**
   * Generate a random token for password reset
   */
  def generateResetToken(): String = {
    java.util.UUID.randomUUID().toString.replace("-", "")
  }
}
