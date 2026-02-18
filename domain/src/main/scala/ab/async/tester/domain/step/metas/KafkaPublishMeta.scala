package ab.async.tester.domain.step.metas

import ab.async.tester.domain.enums.StepType
import ab.async.tester.domain.step.{KafkaMessage, StepMeta}

case class KafkaPublishMeta(
    resourceId: String,
    topicName: String,
    messages: List[KafkaMessage]
) extends StepMeta {
  override def stepType: StepType = StepType.KafkaPublish
}
