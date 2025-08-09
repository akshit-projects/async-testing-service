package ab.async.tester.domain.flow

import ab.async.tester.domain.step.FlowStep
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}


case class Floww(
                  id: Option[String],
                  name: String,
                  description: Option[String] = None,
                  creator: String,
                  steps: List[FlowStep],
                  createdAt: Long,
                  modifiedAt: Long,
                  version: Int
                )

object Floww {
  implicit val flowEncoder: Encoder[Floww] = deriveEncoder
  implicit val flowDecoder: Decoder[Floww] = deriveDecoder
}
