package ab.async.tester.service.flows

import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.enums.StepStatus.IN_PROGRESS
import ab.async.tester.domain.execution.{Execution, ExecutionStep}
import ab.async.tester.domain.flow.Floww
import ab.async.tester.domain.requests.RunFlowRequest
import ab.async.tester.domain.step.{DelayStepMeta, FlowStep, HttpStepMeta, KafkaPublishMeta, KafkaSubscribeMeta, LokiStepMeta, RedisStepMeta, SqlStepMeta}

import java.time.Instant

object FlowServiceAdapter {

  /** Extracts all variable references from flow steps
    */
  def extractVariableReferencesFromSteps(steps: List[FlowStep]): Set[String] = {
    val variablePattern = """\$\{variables\.([^}]+)\}""".r
    val allText = extractAllTextFromSteps(steps)
    variablePattern.findAllMatchIn(allText).map(_.group(1)).toSet
  }

  /** Extracts all text content from flow steps for variable reference analysis
    */
  private def extractAllTextFromSteps(steps: List[FlowStep]): String = {
    val textBuilder = new StringBuilder()

    steps.foreach { step =>
      step.meta match {
        case httpMeta: HttpStepMeta =>
          httpMeta.headers.map(h =>
            h.values.map(v => textBuilder.append(v).append(" "))
          )
          httpMeta.body.foreach(b => textBuilder.append(b).append(" "))

        case kafkaSubMeta: KafkaSubscribeMeta =>
          textBuilder.append(kafkaSubMeta.topicName).append(" ")
          textBuilder.append(kafkaSubMeta.groupId).append(" ")

        case kafkaPubMeta: KafkaPublishMeta =>
          textBuilder.append(kafkaPubMeta.topicName).append(" ")
          kafkaPubMeta.messages.map { message =>
            message.key.foreach(k => textBuilder.append(k).append(" "))
            textBuilder.append(message.value).append(" ")
          }

        case sqlMeta: SqlStepMeta =>
          textBuilder.append(sqlMeta.query).append(" ")
          sqlMeta.parameters.foreach(
            _.values.foreach(v => textBuilder.append(v).append(" "))
          )

        case redisMeta: RedisStepMeta =>
          textBuilder.append(redisMeta.key).append(" ")
          redisMeta.value.foreach(v => textBuilder.append(v).append(" "))
          redisMeta.field.foreach(f => textBuilder.append(f).append(" "))

        case lokiMeta: LokiStepMeta =>
          lokiMeta.containsPatterns.foreach(c =>
            textBuilder.append(c).append(" ")
          )
          lokiMeta.notContainsPatterns.foreach(c =>
            textBuilder.append(c).append(" ")
          )
          textBuilder.append(lokiMeta.limit).append(" ")
        case _: DelayStepMeta =>
        // No text content in delay steps
      }
    }

    textBuilder.toString()
  }

  /** Creates execution object from flow and run flow request.
    */
  def createExecutionEntity(
      executionId: String,
      flow: Floww,
      runFlowRequest: RunFlowRequest
  ): Execution = {
    val now = Instant.now()
    val execSteps = flow.steps.map { step =>
      ExecutionStep(
        id = step.id,
        name = step.name,
        stepType = step.stepType,
        meta = step.meta,
        timeoutMs = step.timeoutMs,
        runInBackground = step.runInBackground,
        continueOnSuccess = step.continueOnSuccess,
        status = IN_PROGRESS,
        startedAt = now,
        logs = List.empty,
        response = None
      )
    }
    Execution(
      id = executionId,
      flowId = flow.id.get,
      flowVersion = flow.version,
      status = ExecutionStatus.Todo,
      startedAt = now,
      steps = execSteps,
      updatedAt = now,
      parameters = Option(runFlowRequest.params),
      variables =
        runFlowRequest.variables.map(v => v.copy(value = v.value.trim.strip())),
      testSuiteExecutionId = runFlowRequest.testSuiteExecutionId
    )
  }

}
