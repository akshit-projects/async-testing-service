package ab.async.tester.workers.app.runner

import ab.async.tester.domain.enums.StepStatus
import ab.async.tester.domain.execution.ExecutionStep
import ab.async.tester.domain.step.{DelayResponse, DelayStepMeta, FlowStep, StepResponse}
import ab.async.tester.library.substitution.VariableSubstitutionService
import akka.actor.ActorSystem
import akka.pattern.after
import com.google.inject.{Inject, Singleton}

import scala.concurrent.duration.DurationLong
import scala.concurrent.{ExecutionContext, Future}


/**
 * Delay step runner that waits for a specified time
 */
@Singleton
class DelayStepRunner @Inject()(
  protected val variableSubstitutionService: VariableSubstitutionService
)(implicit ec: ExecutionContext, system: ActorSystem) extends BaseStepRunner {
  override protected val runnerName: String = "DelayStepRunner"
  
  override protected def executeStep(step: ExecutionStep, previousResults: List[StepResponse]): Future[StepResponse] = {
    try {
      val meta = step.meta
      
      // Extract delay duration
      val delayStepMeta = meta match {
        case delayMeta: DelayStepMeta => delayMeta
        case _ =>
          logger.error(s"Invalid input for step: ${step.name} for step: $step")
          throw new Exception(s"Invalid input for step: ${step.name}")
      }

      val delayMs = delayStepMeta.delayMs
      
      if (delayMs <= 0) {
        Future.failed(new IllegalArgumentException("Delay duration must be positive"))
      } else {
        // Create a delayed future
        logger.info(s"Delaying step ${step.name} for ${delayMs}ms")

        after(delayMs.milliseconds, using = system.scheduler) {
          Future.successful(
            StepResponse(
              name = step.name,
              id = step.id.getOrElse(""),
              status = StepStatus.SUCCESS,
              response = DelayResponse(true)
            )
          )
        }
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error in delay step ${step.name}: ${e.getMessage}", e)
        Future.failed(e)
    }
  }
}
