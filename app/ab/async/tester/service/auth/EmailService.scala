package ab.async.tester.service.auth
import com.google.inject.Inject
import jakarta.mail._
import jakarta.mail.internet._
import play.api.Configuration

import java.util.Properties

class EmailService @Inject() (configuration: Configuration) {

  private val email = configuration.get[String]("services.email.email")
  private val password = configuration.get[String]("services.email.password")
  private val props = new Properties()
  props.put("mail.smtp.host", "smtp.gmail.com")
  props.put("mail.smtp.port", "587")
  props.put("mail.smtp.auth", "true")
  props.put("mail.smtp.starttls.enable", "true")

  private val session = Session.getInstance(
    props,
    new Authenticator {
      override protected def getPasswordAuthentication =
        new PasswordAuthentication(email, password)
    }
  )

  def sendEmail(to: String, subject: String, body: String): Unit = {
    val message = new MimeMessage(session)
    message.setFrom(new InternetAddress(email))
    message.setRecipients(Message.RecipientType.TO, to)
    message.setSubject(subject)
    message.setText(body)
    Transport.send(message)
  }
}
