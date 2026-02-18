package ab.async.tester.domain.step.metas

import ab.async.tester.domain.enums.StepType
import ab.async.tester.domain.step.{StepMeta, StepResponse}
import ab.async.tester.domain.variable.VariableValue

case class DelayStepMeta(
    delayMs: Long
) extends StepMeta {
  override def stepType: StepType = StepType.Delay
}
