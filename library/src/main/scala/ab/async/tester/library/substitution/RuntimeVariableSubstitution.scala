package ab.async.tester.library.substitution

import ab.async.tester.domain.step._
import ab.async.tester.domain.variable.{VariableValue, VariableDataType}
import play.api.Logger

/** Handles runtime variable substitution in flow steps
  */
object RuntimeVariableSubstitution {
  private val logger = Logger(this.getClass)

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
    meta match {
      case httpMeta: HttpStepMeta =>
        httpMeta.copy(
          headers = httpMeta.headers.map(_.map { case (k, v) =>
            k -> substituteInString(v, variableMap)
          }),
          body = httpMeta.body.map(substituteInString(_, variableMap))
        )

      case kafkaSubMeta: KafkaSubscribeMeta =>
        kafkaSubMeta.copy(
          topicName = substituteInString(kafkaSubMeta.topicName, variableMap)
        )

      case kafkaPubMeta: KafkaPublishMeta =>
        kafkaPubMeta.copy(
          topicName = substituteInString(kafkaPubMeta.topicName, variableMap),
          messages = kafkaPubMeta.messages.map { message =>
            message.copy(
              key = message.key.map(k => substituteInString(k, variableMap)),
              value = substituteInString(message.value, variableMap)
            )
          }
        )

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

  /** Substitutes variables in a string using ${variables.variableName} pattern
    */
  private def substituteInString(
      input: String,
      variableMap: Map[String, VariableValue]
  ): String = {
    val variablePattern = """\$\{variables\.([^}]+)\}""".r

    variablePattern.replaceAllIn(
      input,
      { matchResult =>
        val variableName = matchResult.group(1)
        variableMap.get(variableName) match {
          case Some(variable) =>
            val valueStr = substituteVariable(variable)
            logger.debug(
              s"Substituting variable '$variableName' with value '$valueStr'"
            )
            java.util.regex.Matcher.quoteReplacement(valueStr)
          case None =>
            logger.warn(
              s"Variable '$variableName' not found in runtime variables"
            )
            matchResult.matched // keep original
        }
      }
    )
  }

  /** Converts variable to its string representation based on type
    */
  private def substituteVariable(variable: VariableValue): String = {
    variable.`type` match {
      case VariableDataType.STRING  => variable.value.toString
      case VariableDataType.INTEGER => variable.value.asInstanceOf[Int].toString
      case VariableDataType.DOUBLE  =>
        variable.value.asInstanceOf[Double].toString
      case VariableDataType.BOOLEAN =>
        variable.value.asInstanceOf[Boolean].toString
      case VariableDataType.DATE =>
        variable.value.toString // or format as yyyy-MM-dd
    }
  }

  // --- existing extract/validate methods remain unchanged ---
}
