package ab.async.tester.library.utils.stepmeta

import ab.async.tester.domain.step._
import ab.async.tester.domain.step.metas._
import ab.async.tester.domain.variable.VariableValue
import ab.async.tester.library.utils.VariableSubstitution._

import scala.collection.mutable

object StepMetaExtensions {
  implicit class StepMetaOps(val meta: StepMeta) extends AnyVal {

    def getResourceId: Option[String] = meta match {
      case m: HttpStepMeta       => Some(m.resourceId)
      case m: SqlStepMeta        => Some(m.resourceId)
      case m: KafkaPublishMeta   => Some(m.resourceId)
      case m: KafkaSubscribeMeta => Some(m.resourceId)
      case m: LokiStepMeta       => Some(m.resourceId)
      case m: RedisStepMeta      => Some(m.resourceId)
      case _                     => None
    }

    def extractVariableFields(): List[String] = meta match {
      case m: HttpStepMeta =>
        val list = mutable.ListBuffer[String]()
        m.headers.map(h => h.values.map(list.addOne))
        m.body.foreach(list.addOne)
        list.toList

      case m: SqlStepMeta =>
        val list = mutable.ListBuffer[String]()
        list.addOne(m.query)
        m.parameters.foreach(_.values.foreach(list.addOne))
        list.toList

      case m: KafkaPublishMeta =>
        val list = mutable.ListBuffer[String]()
        list.addOne(m.topicName)
        m.messages.map { message =>
          message.key.foreach(list.addOne)
          list.addOne(message.value)
        }
        list.toList

      case m: KafkaSubscribeMeta =>
        val list = mutable.ListBuffer[String]()
        list.addOne(m.topicName)
        list.addOne(m.groupId)
        list.toList

      case m: RedisStepMeta =>
        List(
          Some(m.key),
          m.value,
          m.field,
          m.expectedValue
        ).flatten ++ m.fields.map(_.values).getOrElse(Nil)

      case m: LokiStepMeta =>
        val list = mutable.ListBuffer[String]()
        m.labels.values.foreach(v => list.addOne(v))
        m.containsPatterns.foreach(c => list.addOne(c))
        m.notContainsPatterns.foreach(c => list.addOne(c))
        list.addOne(m.limit.toString)
        list.toList

      case m: ConditionStepMeta =>
        val list = mutable.ListBuffer[String]()
        m.branches.foreach { branch =>
          list.addOne(branch.condition.left)
          list.addOne(branch.condition.right)
        }
        list.toList

      case _: DelayStepMeta => List.empty
      case _                => List.empty
    }

    def extractStringFieldsFromStepMeta(): List[String] = meta match {
      case m: HttpStepMeta =>
        List(m.body, m.expectedStatus, m.expectedResponse).flatten ++ m.headers
          .map(_.values)
          .getOrElse(Nil)

      case m: SqlStepMeta =>
        List(m.query) ++ m.parameters.map(_.values).getOrElse(Nil)

      case m: KafkaPublishMeta =>
        m.topicName :: m.messages.flatMap(msg =>
          List(msg.key, Some(msg.value)).flatten
        )

      case m: KafkaSubscribeMeta =>
        List(m.topicName, m.groupId)

      case m: RedisStepMeta =>
        List(Some(m.key), m.value, m.field, m.expectedValue).flatten ++ m.fields
          .map(_.values)
          .getOrElse(Nil)

      case m: LokiStepMeta =>
        List(
          m.namespace
        ) ++ m.labels.values ++ m.containsPatterns ++ m.notContainsPatterns

      case m: ConditionStepMeta =>
        m.branches.flatMap { branch =>
          List(branch.condition.left, branch.condition.right)
        }

      case _: DelayStepMeta => List.empty
      case _                => List.empty
    }

    def substituteVariablesInStepMeta(
        variableMap: Map[String, VariableValue]
    ): StepMeta = meta match {
      case m: HttpStepMeta =>
        m.copy(
          headers = m.headers.map(_.map { case (k, v) =>
            k -> substituteInString(v, variableMap)
          }),
          body = m.body.map(substituteInString(_, variableMap))
        )

      case m: SqlStepMeta =>
        m.copy(
          query = substituteInString(m.query, variableMap),
          parameters = m.parameters.map(_.map { case (k, v) =>
            k -> substituteInString(v, variableMap)
          })
        )

      case m: KafkaPublishMeta =>
        m.copy(
          topicName = substituteInString(m.topicName, variableMap),
          messages = m.messages.map { msg =>
            msg.copy(
              key = msg.key.map(substituteInString(_, variableMap)),
              value = substituteInString(msg.value, variableMap)
            )
          }
        )

      case m: KafkaSubscribeMeta =>
        m.copy(topicName = substituteInString(m.topicName, variableMap))

      case m: RedisStepMeta =>
        m.copy(
          key = substituteInString(m.key, variableMap),
          value = m.value.map(substituteInString(_, variableMap)),
          field = m.field.map(substituteInString(_, variableMap)),
          fields = m.fields.map(_.map { case (k, v) =>
            k -> substituteInString(v, variableMap)
          }),
          expectedValue =
            m.expectedValue.map(substituteInString(_, variableMap))
        )

      case m: LokiStepMeta =>
        m.copy(
          namespace = substituteInString(m.namespace, variableMap),
          labels = m.labels.map { case (k, v) =>
            k -> substituteInString(v, variableMap)
          },
          containsPatterns =
            m.containsPatterns.map(substituteInString(_, variableMap)),
          notContainsPatterns =
            m.notContainsPatterns.map(substituteInString(_, variableMap))
        )

      case m: ConditionStepMeta =>
        m.copy(
          branches = m.branches.map { b =>
            b.copy(condition =
              b.condition.copy(
                left = substituteInString(b.condition.left, variableMap),
                right = substituteInString(b.condition.right, variableMap)
              )
            )
          }
        )

      case m: DelayStepMeta => m
      case _                => meta
    }

    def substituteResponsesInStepMeta(
        stepResponses: Map[String, StepResponse]
    ): StepMeta = meta match {
      case m: HttpStepMeta =>
        m.copy(
          body = m.body.map(substituteVariables(_, stepResponses)),
          headers = m.headers.map(_.map { case (k, v) =>
            k -> substituteVariables(v, stepResponses)
          }),
          expectedStatus =
            m.expectedStatus.map(substituteVariables(_, stepResponses)),
          expectedResponse =
            m.expectedResponse.map(substituteVariables(_, stepResponses))
        )

      case m: SqlStepMeta =>
        m.copy(
          query = substituteVariables(m.query, stepResponses),
          parameters = m.parameters.map(_.map { case (k, v) =>
            k -> substituteVariables(v, stepResponses)
          })
        )

      case m: KafkaPublishMeta =>
        m.copy(
          topicName = substituteVariables(m.topicName, stepResponses),
          messages = m.messages.map { msg =>
            msg.copy(
              key = msg.key.map(substituteVariables(_, stepResponses)),
              value = substituteVariables(msg.value, stepResponses)
            )
          }
        )

      case m: KafkaSubscribeMeta =>
        m.copy(
          topicName = substituteVariables(m.topicName, stepResponses),
          groupId = substituteVariables(m.groupId, stepResponses)
        )

      case m: RedisStepMeta =>
        m.copy(
          key = substituteVariables(m.key, stepResponses),
          value = m.value.map(substituteVariables(_, stepResponses)),
          field = m.field.map(substituteVariables(_, stepResponses)),
          fields = m.fields.map(_.map { case (k, v) =>
            k -> substituteVariables(v, stepResponses)
          }),
          expectedValue =
            m.expectedValue.map(substituteVariables(_, stepResponses))
        )

      case m: LokiStepMeta =>
        m.copy(
          namespace = substituteVariables(m.namespace, stepResponses),
          labels = m.labels.map { case (k, v) =>
            k -> substituteVariables(v, stepResponses)
          },
          containsPatterns =
            m.containsPatterns.map(substituteVariables(_, stepResponses)),
          notContainsPatterns =
            m.notContainsPatterns.map(substituteVariables(_, stepResponses))
        )

      case m: ConditionStepMeta =>
        m.copy(
          branches = m.branches.map { b =>
            b.copy(condition =
              b.condition.copy(
                left = substituteVariables(b.condition.left, stepResponses),
                right = substituteVariables(b.condition.right, stepResponses)
              )
            )
          }
        )

      case m: DelayStepMeta => m
      case _                => meta
    }
  }
}
