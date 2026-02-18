package ab.async.tester.domain.step.metas

import ab.async.tester.domain.enums.{ConditionOperator, StepType}
import ab.async.tester.domain.step.StepMeta
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class ConditionStepMeta(
    branches: List[ConditionalBranch],
    elseFlowId: Option[String] = None,
    terminateOnNoMatch: Boolean = true
) extends StepMeta {
  override def stepType: StepType = StepType.Condition
}

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
  implicit val conditionBranchEncoder: Encoder[ConditionalBranch] = deriveEncoder
  implicit val conditionDecoder: Decoder[Condition] = deriveDecoder
  implicit val conditionBranchDecoder: Decoder[ConditionalBranch] = deriveDecoder
  implicit val stepMetaEncoder: Encoder[ConditionStepMeta] = deriveEncoder
  implicit val stepMetaDecoder: Decoder[ConditionStepMeta] = deriveDecoder
}