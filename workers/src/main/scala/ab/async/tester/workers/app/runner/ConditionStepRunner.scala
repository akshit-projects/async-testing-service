package ab.async.tester.workers.app.runner

import ab.async.tester.domain.enums.{ConditionOperator, StepStatus}
import ab.async.tester.domain.execution.ExecutionStep
import ab.async.tester.domain.step.metas.ConditionStepMeta
import ab.async.tester.domain.step.{
  ConditionResponse,
  StepResponse
}
import ab.async.tester.library.repository.flow.FlowRepository
import ab.async.tester.library.substitution.VariableSubstitutionService
import com.google.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** Runner for Condition steps
  */
@Singleton
class ConditionStepRunner @Inject() (
    val variableSubstitutionService: VariableSubstitutionService,
    val flowRepository: FlowRepository
)(implicit ec: ExecutionContext)
    extends BaseStepRunner {

  override val runnerName: String = "ConditionStepRunner"

  override protected def executeStep(
      step: ExecutionStep,
      previousResults: List[StepResponse]
  ): Future[StepResponse] = {

    val meta = step.meta.asInstanceOf[ConditionStepMeta]

    // Iterate through branches to find the first match
    val matchedBranchIndex = meta.branches.indexWhere { branch =>
      evaluateCondition(
        branch.condition.left,
        branch.condition.operator,
        branch.condition.right
      )
    }

    if (matchedBranchIndex >= 0) {
      // Match found
      val branch = meta.branches(matchedBranchIndex)
      logger.info(
        s"Condition matched branch $matchedBranchIndex, fetching flow ${branch.flowId}"
      )

      flowRepository.findById(branch.flowId).map {
        case Some(flow) =>
          createSuccessResponse(
            step,
            ConditionResponse(
              success = true,
              matchedChoice = matchedBranchIndex.toString,
              generatedSteps = flow.steps
            )
          )
        case None =>
          createErrorResponse(step, s"Linked flow ${branch.flowId} not found")
      }
    } else {
      // No match found, check else flow
      meta.elseFlowId match {
        case Some(flowId) =>
          logger.info("Condition matched 'else' branch")
          flowRepository.findById(flowId).map {
            case Some(flow) =>
              createSuccessResponse(
                step,
                ConditionResponse(
                  success = true,
                  matchedChoice = "else",
                  generatedSteps = flow.steps
                )
              )
            case None =>
              createErrorResponse(step, s"Linked else flow $flowId not found")
          }
        case None =>
          if (meta.terminateOnNoMatch) {
            logger.info("No condition matched and terminateOnNoMatch is true")
            Future.successful(
              createErrorResponse(
                step,
                "No condition matched and terminateOnNoMatch is true"
              )
            )
          } else {
            logger.info(
              "No condition matched, continuing without executing any branch"
            )
            Future.successful(
              createSuccessResponse(
                step,
                ConditionResponse(
                  success = true,
                  matchedChoice = "none",
                  generatedSteps = List.empty
                )
              )
            )
          }
      }
    }
  }

  private def evaluateCondition(
      left: String,
      operator: ConditionOperator,
      right: String
  ): Boolean = {
    logger.debug(
      s"Evaluating condition: '$left' ${operator.stringified} '$right'"
    )

    operator match {
      case ConditionOperator.Equals =>
        left == right

      case ConditionOperator.NotEquals =>
        left != right

      case ConditionOperator.Contains =>
        left.contains(right)

      case ConditionOperator.GreaterThan =>
        Try(left.toDouble > right.toDouble).getOrElse {
          logger.warn(
            s"Failed to parse operands as numbers for > comparison: $left, $right"
          )
          false
        }

      case ConditionOperator.LessThan =>
        Try(left.toDouble < right.toDouble).getOrElse {
          logger.warn(
            s"Failed to parse operands as numbers for < comparison: $left, $right"
          )
          false
        }

      case ConditionOperator.Exists =>
        // For exists, we check if the left operand is not empty/null
        // The 'right' operand is ignored for 'exists' check usually, or treated as 'true' check
        // Assuming 'exists' means the value is non-empty/non-null string
        Option(left).exists(_.nonEmpty) && left != "null"
    }
  }
}
