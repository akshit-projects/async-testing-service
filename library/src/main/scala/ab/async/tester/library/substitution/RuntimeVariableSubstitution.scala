package ab.async.tester.library.substitution

import ab.async.tester.domain.step._
import ab.async.tester.domain.step.metas.{ConditionStepMeta, DelayStepMeta, KafkaPublishMeta, KafkaSubscribeMeta, LokiStepMeta, RedisStepMeta, SqlStepMeta}
import ab.async.tester.domain.variable.{VariableDataType, VariableValue}
import ab.async.tester.library.utils.VariableSubstitution.substituteInString
import ab.async.tester.library.utils.stepmeta.StepMetaExtensions.StepMetaOps
import play.api.Logger

/** Handles runtime variable substitution in flow steps
  */
object RuntimeVariableSubstitution {

  /** Substitutes runtime variables in all steps of a flow
    */
  def substituteVariablesInSteps(
      steps: List[FlowStep],
      variables: List[VariableValue]
  ): List[FlowStep] = {
    val variableMap: Map[String, VariableValue] =
      variables.map(v => v.name -> v).toMap

    steps.map { step =>
      val updatedMeta = substituteVariablesInStepMeta(step.meta, variableMap)
      step.copy(meta = updatedMeta)
    }
  }

  /** Substitutes variables in step metadata
    */
  private def substituteVariablesInStepMeta(
      meta: StepMeta,
      variableMap: Map[String, VariableValue]
  ): StepMeta = {
    return meta.substituteVariablesInStepMeta(variableMap)
    meta match {

      case sqlMeta: SqlStepMeta =>
        sqlMeta.copy(
          query = substituteInString(sqlMeta.query, variableMap),
          parameters = sqlMeta.parameters.map(_.map { case (k, v) =>
            k -> substituteInString(v, variableMap)
          }),
          expectedRowCount =
            sqlMeta.expectedRowCount // keep as-is unless it needs substitution
        )

      case redisMeta: RedisStepMeta =>
        redisMeta.copy(
          key = substituteInString(redisMeta.key, variableMap),
          value = redisMeta.value.map(substituteInString(_, variableMap)),
          field = redisMeta.field.map(substituteInString(_, variableMap)),
          expectedValue =
            redisMeta.expectedValue.map(substituteInString(_, variableMap))
        )

      case lokiMeta: LokiStepMeta =>
        lokiMeta.copy(
          namespace = substituteInString(lokiMeta.namespace, variableMap),
          labels = lokiMeta.labels.map { case (k, v) =>
            k -> substituteInString(v, variableMap)
          },
          containsPatterns =
            lokiMeta.containsPatterns.map(substituteInString(_, variableMap)),
          notContainsPatterns =
            lokiMeta.notContainsPatterns.map(substituteInString(_, variableMap))
        )

      case delayMeta: DelayStepMeta =>
        delayMeta // no substitution needed

      case conditionMeta: ConditionStepMeta =>
        conditionMeta.copy(
          branches = conditionMeta.branches.map { branch =>
            branch.copy(
              condition = branch.condition.copy(
                left = substituteInString(branch.condition.left, variableMap),
                right = substituteInString(branch.condition.right, variableMap)
              )
            )
          }
        )

    }

  }

  // --- existing extract/validate methods remain unchanged ---
}
