package ab.async.tester.models.flow

import ab.async.tester.models.step.FlowStep
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}


case class Floww(
                 name: String,
                 id: Option[String] = None,
                 creator: String,
                 steps: List[FlowStep],
                 createdAt: Long,
                 modifiedAt: Long
               )

object Floww {
  implicit val flowEncoder: Encoder[Floww] = deriveEncoder
  implicit val flowDecoder: Decoder[Floww] = deriveDecoder
}
