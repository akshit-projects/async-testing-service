package ab.async.tester.domain.step.metas

import ab.async.tester.domain.enums.StepType
import ab.async.tester.domain.kafka.KafkaSearchPattern
import ab.async.tester.domain.step.StepMeta

case class KafkaSubscribeMeta(
    resourceId: String,
    topicName: String,
    groupId: String,
    maxMessages: Int,
    fromBeginning: Boolean,
    searchPattern: Option[KafkaSearchPattern] = None
) extends StepMeta {
  override def stepType: StepType = StepType.KafkaSubscribe
}
