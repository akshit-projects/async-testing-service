package ab.async.tester.domain.team

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
 * Represents a team within an organisation
 */
case class Team(
  id: Option[String] = None,
  orgId: String,
  name: String,
  createdAt: Long = System.currentTimeMillis(),
  modifiedAt: Long = System.currentTimeMillis()
)

object Team {
  implicit val teamEncoder: Encoder[Team] = deriveEncoder
  implicit val teamDecoder: Decoder[Team] = deriveDecoder
}
