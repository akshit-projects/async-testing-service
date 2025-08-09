package ab.async.tester.domain.flow

import ab.async.tester.domain.step.FlowStep
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
 * Represents a specific version of a flow with its steps and metadata
 */
case class FlowVersion(
                        id: Option[String] = None,
                        flowId: String,
                        version: Int,
                        steps: List[FlowStep],
                        createdAt: Long,
                        createdBy: String,
                        description: Option[String] = None
                      )

object FlowVersion {
  implicit val flowVersionEncoder: Encoder[FlowVersion] = deriveEncoder
  implicit val flowVersionDecoder: Decoder[FlowVersion] = deriveDecoder
}
