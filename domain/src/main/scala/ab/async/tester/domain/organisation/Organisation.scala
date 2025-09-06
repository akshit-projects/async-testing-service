package ab.async.tester.domain.organisation

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
 * Represents an organisation in the system
 */
case class Organisation(
  id: Option[String] = None,
  name: String,
  createdAt: Long = System.currentTimeMillis(),
  modifiedAt: Long = System.currentTimeMillis()
)

object Organisation {
  implicit val organisationEncoder: Encoder[Organisation] = deriveEncoder
  implicit val organisationDecoder: Decoder[Organisation] = deriveDecoder
}
