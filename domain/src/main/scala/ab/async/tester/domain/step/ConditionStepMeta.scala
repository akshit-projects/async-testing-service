package ab.async.tester.domain.step

import ab.async.tester.domain.enums.ConditionOperator
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class ConditionStepMeta(
    branches: List[ConditionalBranch],
    elseFlowId: Option[String] = None,
    terminateOnNoMatch: Boolean = true
) extends StepMeta

case class ConditionalBranch(
    condition: Condition,
    flowId: String
)

case class Condition(
    left: String,
    operator: ConditionOperator,
    right: String
)

object ConditionStepMeta {
  implicit val conditionEncoder: Encoder[Condition] = deriveEncoder
  implicit val conditionDecoder: Decoder[Condition] = deriveDecoder

  implicit val branchEncoder: Encoder[ConditionalBranch] = deriveEncoder
  implicit val branchDecoder: Decoder[ConditionalBranch] = deriveDecoder

  implicit val metaEncoder: Encoder[ConditionStepMeta] = deriveEncoder
  implicit val metaDecoder: Decoder[ConditionStepMeta] = deriveDecoder
}
