package ab.async.tester.workers.app.runner

import ab.async.tester.domain.enums.{StepStatus, StepType}
import ab.async.tester.domain.execution.ExecutionStep
import ab.async.tester.domain.step.{
  FlowStep,
  StepError,
  StepResponse,
  StepResponseValue
}
import ab.async.tester.library.substitution.VariableSubstitutionService
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Logger

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/** Interface for step runners
  */
trait StepRunner {

  /** Run a single step
    *
    * @param step
    *   the step to run
    * @param previousResults
    *   previous step results
    * @return
    *   a future containing the step response
    */
  def runStep(
      step: ExecutionStep,
      previousResults: List[StepResponse]
  ): Future[StepResponse]
}

/** Registry for step runners
  */
@ImplementedBy(classOf[StepRunnerRegistryImpl])
trait StepRunnerRegistry {

  /** Get a runner for a specific step function
    *
    * @return
    *   the appropriate runner for the step
    */
  def getRunnerForStep(stepType: StepType): StepRunner

  /** Register a step runner
    *
    * @param function
    *   the step function
    * @param runner
    *   the runner implementation
    */
  def registerRunner(function: String, runner: StepRunner): Unit
}

/** Implementation of step runner registry
  */
@Singleton
class StepRunnerRegistryImpl @Inject() (
    httpStepRunner: HttpStepRunner,
    delayStepRunner: DelayStepRunner,
    kafkaPublisherStepRunner: KafkaPublisherStepRunner,
    kafkaConsumerStepRunner: KafkaConsumerStepRunner,
    sqlStepRunner: SqlStepRunner,
    redisStepRunner: RedisStepRunner,
    lokiStepRunner: LokiStepRunner
) extends StepRunnerRegistry {

  private val runners = mutable.Map[String, StepRunner]()

  // Register default runners by StepType
  registerRunner(StepType.HttpRequest.toString, httpStepRunner)
  registerRunner(StepType.Delay.toString, delayStepRunner)
  registerRunner(StepType.KafkaPublish.toString, kafkaPublisherStepRunner)
  registerRunner(StepType.KafkaSubscribe.toString, kafkaConsumerStepRunner)
  registerRunner(StepType.SqlQuery.toString, sqlStepRunner)
  registerRunner(StepType.RedisOperation.toString, redisStepRunner)
  registerRunner(StepType.LokiLogSearch.toString, lokiStepRunner)

  /** Get a runner for a specific step function
    */
  override def getRunnerForStep(stepType: StepType): StepRunner = {
    val key = stepType.toString.toLowerCase().replace("$", "")
    runners.getOrElse(
      key,
      throw new IllegalArgumentException(
        s"No runner found for step type: $stepType"
      )
    )
  }

  /** Register a step runner
    */
  override def registerRunner(function: String, runner: StepRunner): Unit = {
    if (runner != null) {
      runners.put(function.toLowerCase(), runner)
    }
  }
}

/** Base class for all step runners with common functionality
  */
abstract class BaseStepRunner(implicit ec: ExecutionContext)
    extends StepRunner {

  protected implicit val logger: Logger = Logger(this.getClass)
  protected val runnerName: String

  // Variable substitution service - will be injected by subclasses
  protected def variableSubstitutionService: VariableSubstitutionService

  /** Run a single step with timeout handling
    */
  override def runStep(
      step: ExecutionStep,
      previousResults: List[StepResponse]
  ): Future[StepResponse] = {
    MetricUtils.withAsyncServiceMetrics(runnerName, "runStep") {
      // Create a future for the step execution with timeout
      val timeoutMillis = step.timeoutMs
      val startTime = System.currentTimeMillis()

      logger.info(
        s"Running step ${step.name} (function: ${step.stepType}, background: ${step.runInBackground})"
      )

      // Apply variable substitution before executing the step
      val substitutedStep =
        try {
          variableSubstitutionService.substituteVariablesInStep(
            step,
            previousResults
          )
        } catch {
          case e: Exception =>
            logger.error(
              s"Variable substitution failed for step ${step.name}: ${e.getMessage}",
              e
            )
            return Future.successful(
              createErrorResponse(
                step,
                s"Variable substitution failed: ${e.getMessage}"
              )
            )
        }

      val resultFuture = executeStep(substitutedStep, previousResults)
        .map { result =>
          val endTime = System.currentTimeMillis()
          val duration = endTime - startTime
          logger.info(
            s"Step ${step.name} completed in ${duration}ms with status ${result.status}"
          )
          result
        }
        .recover { case e: Exception =>
          logger.error(s"Step ${step.name} failed: ${e.getMessage}", e)
          createErrorResponse(step, e.getMessage)
        }

      // For background steps, we might not want to apply timeout
      if (step.runInBackground) {
        // Background steps should either complete quickly or run indefinitely
        resultFuture
      } else {
        // Add timeout handling for regular steps
        val timeoutFuture = Future {
          Thread.sleep(timeoutMillis + 2000) // 1s buffer
          createErrorResponse(step, s"Step timed out after ${timeoutMillis}ms")
        }

        // Return the first result (either completion or timeout)
        Future.firstCompletedOf(List(resultFuture, timeoutFuture))
      }
    }
  }

  /** Execute the step (to be implemented by subclasses)
    */
  protected def executeStep(
      step: ExecutionStep,
      previousResults: List[StepResponse]
  ): Future[StepResponse]

  /** Create an error response for a step
    */
  protected def createErrorResponse(
      step: ExecutionStep,
      errorMessage: String
  ): StepResponse = {
    StepResponse(
      name = step.name,
      id = step.id.getOrElse(""),
      status = StepStatus.ERROR,
      response = StepError(
        error = errorMessage,
        expectedValue = None,
        actualValue = None
      )
    )
  }

  /** Create a success response for a step
    */
  protected def createSuccessResponse[T <: StepResponseValue](
      step: ExecutionStep,
      data: T
  ): StepResponse = {
    StepResponse(
      name = step.name,
      id = step.id.getOrElse(""),
      status = StepStatus.SUCCESS,
      response = data
    )
  }
}
