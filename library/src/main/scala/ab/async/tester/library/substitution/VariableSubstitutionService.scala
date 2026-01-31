package ab.async.tester.library.substitution

import ab.async.tester.domain.execution.ExecutionStep
import ab.async.tester.domain.step._
import com.google.inject.{Inject, Singleton}
import play.api.Logger

/** Service for performing variable substitution in step execution
  */
@Singleton
class VariableSubstitutionService @Inject() () {

  private val logger = Logger(this.getClass)

  /** Apply variable substitution to a step's meta before execution
    * @param step
    *   The execution step
    * @param previousResults
    *   Map of step name to step response for variable resolution
    * @return
    *   ExecutionStep with variables substituted in meta
    */
  def substituteVariablesInStep(
      step: ExecutionStep,
      previousResults: List[StepResponse]
  ): ExecutionStep = {
    // Convert previous results to a map by step name for easier lookup
    val stepResponseMap = previousResults.map(r => r.name -> r).toMap

    try {
      val substitutedMeta =
        substituteVariablesInStepMeta(step.meta, stepResponseMap)
      step.copy(meta = substitutedMeta)
    } catch {
      case e: Exception =>
        logger.error(
          s"Error substituting variables in step ${step.name}: ${e.getMessage}",
          e
        )
        throw new RuntimeException(
          s"Variable substitution failed for step ${step.name}: ${e.getMessage}",
          e
        )
    }
  }

  /** Substitute variables in step meta based on step type
    */
  private def substituteVariablesInStepMeta(
      meta: StepMeta,
      stepResponses: Map[String, StepResponse]
  ): StepMeta = {
    meta match {
      case httpMeta: HttpStepMeta =>
        substituteVariablesInHttpMeta(httpMeta, stepResponses)
      case kafkaSubMeta: KafkaSubscribeMeta =>
        substituteVariablesInKafkaSubscribeMeta(kafkaSubMeta, stepResponses)
      case kafkaPubMeta: KafkaPublishMeta =>
        substituteVariablesInKafkaPublishMeta(kafkaPubMeta, stepResponses)
      case delayMeta: DelayStepMeta =>
        // DelayStepMeta doesn't have string fields that need substitution
        delayMeta
      case sqlMeta: SqlStepMeta =>
        substituteVariablesInSqlMeta(sqlMeta, stepResponses)
      case redisMeta: RedisStepMeta =>
        substituteVariablesInRedisMeta(redisMeta, stepResponses)
      case lokiMeta: LokiStepMeta =>
        substituteVariablesInLokiMeta(lokiMeta, stepResponses)
      case conditionMeta: ConditionStepMeta =>
        substituteVariablesInConditionMeta(conditionMeta, stepResponses)
    }
  }

  /** Substitute variables in Condition step meta
    */
  private def substituteVariablesInConditionMeta(
      meta: ConditionStepMeta,
      stepResponses: Map[String, StepResponse]
  ): ConditionStepMeta = {
    meta.copy(
      branches = meta.branches.map { branch =>
        branch.copy(
          condition = branch.condition.copy(
            left = VariableSubstitution
              .substituteVariables(branch.condition.left, stepResponses),
            right = VariableSubstitution.substituteVariables(
              branch.condition.right,
              stepResponses
            )
          )
        )
      }
    )

  }

  /** Substitute variables in HTTP step meta
    */
  private def substituteVariablesInHttpMeta(
      meta: HttpStepMeta,
      stepResponses: Map[String, StepResponse]
  ): HttpStepMeta = {
    meta.copy(
      body = meta.body.map(
        VariableSubstitution.substituteVariables(_, stepResponses)
      ),
      headers = meta.headers.map(_.map { case (key, value) =>
        key -> VariableSubstitution.substituteVariables(value, stepResponses)
      }),
      expectedStatus = meta.expectedStatus.map(
        VariableSubstitution.substituteVariables(_, stepResponses)
      ),
      expectedResponse = meta.expectedResponse.map(
        VariableSubstitution.substituteVariables(_, stepResponses)
      )
    )
  }

  /** Substitute variables in Kafka subscribe meta
    */
  private def substituteVariablesInKafkaSubscribeMeta(
      meta: KafkaSubscribeMeta,
      stepResponses: Map[String, StepResponse]
  ): KafkaSubscribeMeta = {
    meta.copy(
      topicName =
        VariableSubstitution.substituteVariables(meta.topicName, stepResponses),
      groupId =
        VariableSubstitution.substituteVariables(meta.groupId, stepResponses)
    )
  }

  /** Substitute variables in Kafka publish meta
    */
  private def substituteVariablesInKafkaPublishMeta(
      meta: KafkaPublishMeta,
      stepResponses: Map[String, StepResponse]
  ): KafkaPublishMeta = {
    meta.copy(
      topicName =
        VariableSubstitution.substituteVariables(meta.topicName, stepResponses),
      messages = meta.messages.map { msg =>
        msg.copy(
          key = msg.key.map(
            VariableSubstitution.substituteVariables(_, stepResponses)
          ),
          value =
            VariableSubstitution.substituteVariables(msg.value, stepResponses)
        )
      }
    )
  }

  /** Substitute variables in SQL step meta
    */
  private def substituteVariablesInSqlMeta(
      meta: SqlStepMeta,
      stepResponses: Map[String, StepResponse]
  ): SqlStepMeta = {
    meta.copy(
      query =
        VariableSubstitution.substituteVariables(meta.query, stepResponses),
      parameters = meta.parameters.map(_.map { case (key, value) =>
        key -> VariableSubstitution.substituteVariables(value, stepResponses)
      })
    )
  }

  /** Substitute variables in Redis step meta
    */
  private def substituteVariablesInRedisMeta(
      meta: RedisStepMeta,
      stepResponses: Map[String, StepResponse]
  ): RedisStepMeta = {
    meta.copy(
      key = VariableSubstitution.substituteVariables(meta.key, stepResponses),
      value = meta.value.map(
        VariableSubstitution.substituteVariables(_, stepResponses)
      ),
      field = meta.field.map(
        VariableSubstitution.substituteVariables(_, stepResponses)
      ),
      fields = meta.fields.map(_.map { case (key, value) =>
        key -> VariableSubstitution.substituteVariables(value, stepResponses)
      }),
      expectedValue = meta.expectedValue.map(
        VariableSubstitution.substituteVariables(_, stepResponses)
      )
    )
  }

  /** Substitute variables in Redis step meta
    */
  private def substituteVariablesInLokiMeta(
      meta: LokiStepMeta,
      stepResponses: Map[String, StepResponse]
  ): LokiStepMeta = {
    meta.copy(
      containsPatterns = meta.containsPatterns.map(
        VariableSubstitution.substituteVariables(_, stepResponses)
      ),
      notContainsPatterns = meta.notContainsPatterns.map(
        VariableSubstitution.substituteVariables(_, stepResponses)
      ),
      limit = VariableSubstitution
        .substituteVariables(meta.limit.toString, stepResponses)
        .toInt
    )
  }
}
