package ab.async.tester.runners

import ab.async.tester.constants.StepFunctions
import ab.async.tester.models.enums.StepStatus
import ab.async.tester.models.step.{FlowStep, StepError, StepResponse, StepResponseValue}
import ab.async.tester.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Logger

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Interface for step runners
 */
trait StepRunner {
  /**
   * Run a single step
   * 
   * @param step the step to run
   * @param previousResults previous step results
   * @return a future containing the step response
   */
  def runStep(step: FlowStep, previousResults: List[StepResponse]): Future[StepResponse]
}

/**
 * Registry for step runners
 */
@ImplementedBy(classOf[StepRunnerRegistryImpl])
trait StepRunnerRegistry {
  /**
   * Get a runner for a specific step function
   * 
   * @param function the step function
   * @return the appropriate runner for the step
   */
  def getRunnerForStep(function: String): StepRunner
  
  /**
   * Register a step runner
   * 
   * @param function the step function
   * @param runner the runner implementation
   */
  def registerRunner(function: String, runner: StepRunner): Unit
}

/**
 * Implementation of step runner registry
 */
@Singleton
class StepRunnerRegistryImpl @Inject()(
  httpStepRunner: HttpStepRunner,
  delayStepRunner: DelayStepRunner,
  kafkaPublisherStepRunner: KafkaPublisherStepRunner,
  kafkaConsumerStepRunner: KafkaConsumerStepRunner,
)(implicit ec: ExecutionContext) extends StepRunnerRegistry {
  
  private val runners = mutable.Map[String, StepRunner]()
  
  // Register default runners with function constants matching Go implementation
  registerRunner(StepFunctions.HTTP_API_STEP, httpStepRunner)
  registerRunner(StepFunctions.DELAY_STEP, delayStepRunner)
  registerRunner(StepFunctions.PUBLISH_KAFKA_MESSAGE_STEP, kafkaPublisherStepRunner)
  registerRunner(StepFunctions.SUBSCRIBE_KAFKA_MESSAGES_STEP, kafkaConsumerStepRunner)
  
  /**
   * Get a runner for a specific step function
   */
  override def getRunnerForStep(function: String): StepRunner = {
    runners(function.toLowerCase())
  }
  
  /**
   * Register a step runner
   */
  override def registerRunner(function: String, runner: StepRunner): Unit = {
    if (runner != null) {
      runners.put(function.toLowerCase(), runner)
    }
  }
}

/**
 * Base class for all step runners with common functionality
 */
abstract class BaseStepRunner(implicit ec: ExecutionContext) extends StepRunner {
  
  protected implicit val logger: Logger = Logger(this.getClass)
  protected val runnerName: String
  
  /**
   * Run a single step with timeout handling
   */
  override def runStep(step: FlowStep, previousResults: List[StepResponse]): Future[StepResponse] = {
    MetricUtils.withAsyncServiceMetrics(runnerName, "runStep") {
      // Create a future for the step execution with timeout
      val timeoutMillis = step.timeout
      val startTime = System.currentTimeMillis()
      
      logger.info(s"Running step ${step.name} (function: ${step.function}, background: ${step.runInBackground})")
      
      val resultFuture = executeStep(step, previousResults)
        .map { result =>
          val endTime = System.currentTimeMillis()
          val duration = endTime - startTime
          logger.info(s"Step ${step.name} completed in ${duration}ms with status ${result.status}")
          result
        }
        .recover {
          case e: Exception =>
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
  
  /**
   * Execute the step (to be implemented by subclasses)
   */
  protected def executeStep(step: FlowStep, previousResults: List[StepResponse]): Future[StepResponse]
  
  /**
   * Create an error response for a step
   */
  protected def createErrorResponse(step: FlowStep, errorMessage: String): StepResponse = {
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
  
  /**
   * Create a success response for a step
   */
  protected def createSuccessResponse[T <: StepResponseValue](step: FlowStep, data: T): StepResponse = {
    StepResponse(
      name = step.name,
      id = step.id.getOrElse(""),
      status = StepStatus.SUCCESS,
      response = data
    )
  }
} 