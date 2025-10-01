package ab.async.tester.workers.app.runner

import ab.async.tester.domain.enums.StepStatus.IN_PROGRESS
import ab.async.tester.domain.enums.{ExecutionStatus, StepStatus}
import ab.async.tester.domain.execution.{Execution, ExecutionStatusUpdate, ExecutionStep, StepUpdate}
import ab.async.tester.domain.step.{FlowStep, StepError, StepResponse, StepResponseValue}
import ab.async.tester.domain.variable.VariableValue
import ab.async.tester.library.cache.RedisClient
import ab.async.tester.library.constants.Constants.COMPLETED_EXECUTIONS
import ab.async.tester.library.repository.execution.ExecutionRepository
import ab.async.tester.library.repository.testsuite.TestSuiteExecutionRepository
import ab.async.tester.library.substitution.RuntimeVariableSubstitution
import ab.async.tester.library.utils.MetricUtils
import ab.async.tester.workers.app.clients.kafka.KafkaConsumer
import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.scaladsl.Sink
import com.google.inject.{ImplementedBy, Inject, Singleton}
import io.circe.generic.auto._
import io.circe.jawn.decode
import io.circe.syntax._
import play.api.{Configuration, Logger}

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Flow runner trait for worker processes
 */
@ImplementedBy(classOf[FlowRunnerImpl])
trait FlowRunner {
  /**
   * Start consuming Kafka messages and executing flows
   */
  def startFlowConsumer(): Unit

  /**
   * Execute a single execution
   * @param execution the execution to run
   * @return Future indicating completion
   */
  def executeFlow(execution: Execution): Future[Unit]
}

/**
 * Worker implementation that consumes Kafka messages and executes flows
 */
@Singleton
class FlowRunnerImpl @Inject()(
  executionRepository: ExecutionRepository,
  stepRunnerRegistry: StepRunnerRegistry,
  redisClient: RedisClient,
  configuration: Configuration,
  testSuiteExecutionRepository: TestSuiteExecutionRepository
)(implicit system: ActorSystem, ec: ExecutionContext) extends FlowRunner {

  private implicit val logger: Logger = Logger(this.getClass)
  private val runnerName = "FlowRunner"

  // Kafka configuration
  private val workerQueueTopic = configuration.get[String]("events.workerQueueTopic")
  private val redisChannel = "internal-executions-topic"
  
  /**
   * Start consuming Kafka messages and executing flows
   */
  override def startFlowConsumer(): Unit = {
    logger.info(s"Starting FlowRunner consumer for topic: $workerQueueTopic")

    KafkaConsumer.subscribeTopic(workerQueueTopic)
      .mapAsync(10) { msg =>
        val messageValue = msg.record.value()
        logger.debug(s"Received Kafka message: $messageValue")

        // Parse the execution from JSON
        decode[Execution](messageValue) match {
          case Right(execution) =>
            logger.info(s"Processing execution: ${execution.id}")
            val initialDelay = getInitialDelay(execution.parameters).getOrElse(0)
            after(initialDelay.milliseconds, using = system.scheduler) {
              logger.info(s"Starting execution ${execution.id} flow after $initialDelay")
              executeFlow(execution).map { _ =>
                // Commit the Kafka message after successful processing
                msg.committableOffset
              }
            }.recover {
              case e: Exception =>
                logger.error(s"Failed to process execution ${execution.id}: ${e.getMessage}", e)
                // Still commit to avoid reprocessing the same failed message
                msg.committableOffset
            }
          case Left(error) =>
            logger.error(s"Failed to parse execution from Kafka message: $error")
            // Commit malformed messages to avoid infinite reprocessing
            Future.successful(msg.committableOffset)
        }
      }
      .mapAsync(10)(_.commitScaladsl())
      .runWith(Sink.ignore)

    logger.info("FlowRunner consumer started successfully")
  }
  
  /**
   * Execute a single execution
   */
  override def executeFlow(execution: Execution): Future[Unit] = {
    MetricUtils.withAsyncServiceMetrics(runnerName, "executeFlow") {
      logger.info(s"Starting execution: ${execution.id} with ${execution.variables.length} variables")

      // Update execution status to in-progress
      updateStatus(execution, ExecutionStatus.InProgress).flatMap { _ =>
        publishExecutionUpdate(execution.id, "Execution started", ExecutionStatus.InProgress)
        // Execute steps sequentially
        executeFlowSteps(execution.id, execution.steps, execution.variables).map { successful =>
          val finalStatus = if (successful) ExecutionStatus.Completed else ExecutionStatus.Failed
          val message = if (successful) "Execution completed successfully" else "Execution failed"

          // Update final status
          updateStatus(execution, finalStatus).map { _ =>
            publishExecutionUpdate(execution.id, message, finalStatus)
            logger.info(s"Execution ${execution.id} completed with status: $finalStatus")
          }
        }.recoverWith {
          case e: Exception =>
            logger.error(s"Execution ${execution.id} failed with exception: ${e.getMessage}", e)
            publishExecutionUpdate(execution.id, s"Execution failed: ${e.getMessage}", ExecutionStatus.Failed)
            updateStatus(execution, ExecutionStatus.Failed).map { _ =>
              logger.info(s"Execution ${execution.id} completed with status: ${ExecutionStatus.Failed}")
            }
        }.map {_ =>
          logger.info("")
        }
      }
    }

  }

  private def updateStatus(execution: Execution, status: ExecutionStatus) = {
    val isCompleted = COMPLETED_EXECUTIONS.contains(status)
    val updateExecutionStatus = executionRepository.updateStatus(execution.id, status, isCompleted)
    val updateTestSuiteExecutionStatus = execution.testSuiteExecutionId match {
      case Some(testSuiteExecutionId) =>
        testSuiteExecutionRepository.updateTestSuiteExecution(testSuiteExecutionId, execution.id, status)
      case None => Future.successful(())
    }

    for {
      _ <- updateExecutionStatus
      _ <- updateTestSuiteExecutionStatus
    } yield {
      logger.info(s"Updated status for execution ${execution.id} with status $status")
    }
  }

  private def getInitialDelay(parameters: Option[Map[String, String]]) = {
    parameters.flatMap { params =>
      params.get("initialDelay").map(_.toInt)
    }
  }

  /**
   * Execute steps sequentially with Redis pub/sub updates
   */
  private def executeFlowSteps(executionId: String, steps: List[ExecutionStep], variables: List[VariableValue] = List.empty): Future[Boolean] = {
    // Tracks results of all completed steps by name
    val stepResults = MutableMap.empty[String, StepResponse]

    // Tracks currently running background steps by name
    val backgroundSteps = MutableMap.empty[String, Future[StepResponse]]

    // Execute a single step and handle background behavior
    def executeStep(step: ExecutionStep): Future[StepResponse] = {
      logger.info(s"Executing step ${step.name} (background: ${step.runInBackground})")

      val stepId = step.id.get
      val updatedStep = step.copy(status = IN_PROGRESS) // TODO add updatedAt

      // Apply runtime variable substitution to the step
      val steps = if (variables.nonEmpty) {
        val flowStep = FlowStep(
          name = step.name,
          stepType = step.stepType,
          meta = step.meta,
          timeoutMs = step.timeoutMs,
          runInBackground = step.runInBackground,
          continueOnSuccess = step.continueOnSuccess
        )
        val substitutedSteps = RuntimeVariableSubstitution.substituteVariablesInSteps(List(flowStep), variables)
        step.copy(meta = substitutedSteps.head.meta)
      } else {
        step
      }

      // Publish step started update
      executionRepository.updateExecutionStep(executionId, stepId, updatedStep).flatMap { _ =>
        publishStepUpdate(executionId, stepId, "Step started", StepStatus.IN_PROGRESS)

        // Find the appropriate runner for this step
        val runner = stepRunnerRegistry.getRunnerForStep(step.stepType)

        // Execute the step with the current results (using the step with substituted variables)
        val allResults = stepResults.values.toList
        runner.runStep(steps, allResults)
      }
    }
    
    // Execute steps sequentially
    def processStepsSequentially(remainingSteps: List[ExecutionStep]): Future[Boolean] = {
      if (remainingSteps.isEmpty) {
        // Check if any background steps are still running
        if (backgroundSteps.isEmpty) {
          Future.successful(true)
        } else {
          logger.info(s"Waiting for ${backgroundSteps.size} background steps to complete")
          // Create a future that completes when all background steps complete
          val backgroundFutures = backgroundSteps.values.toList
          Future.sequence(backgroundFutures).map(_ => true)
        }
      } else {
        val currentStep = remainingSteps.head
        val nextSteps = remainingSteps.tail

        if (currentStep.runInBackground) {
          // Execute background step without waiting for it
          val stepFuture = executeStep(currentStep)

          // Track the background step
          backgroundSteps += (currentStep.id.get -> stepFuture)

          // Handle background step completion
          stepFuture.onComplete {
            case Success(response) =>
              val updatedStep = currentStep.copy(status = response.status, response = Some(response.response))
              executionRepository.updateExecutionStep(executionId, currentStep.id.get, updatedStep).map { _ =>
                publishStepUpdate(executionId, response.id, "Step completed", response.status)
                stepResults += (currentStep.id.get -> response)
                backgroundSteps -= currentStep.id.get

                if (response.status == StepStatus.ERROR && !currentStep.continueOnSuccess) {
                  logger.error(s"Background step ${currentStep.name} failed with error status")
                }
              }
            case Failure(e) =>
              logger.error(s"Background step ${currentStep.name} failed: ${e.getMessage}", e)

              val errorResponse = StepResponse(
                name = currentStep.name,
                id = currentStep.id.getOrElse(""),
                status = StepStatus.ERROR,
                response = StepError(
                  error = e.getMessage,
                  expectedValue = None,
                  actualValue = None
                )
              )
              val updatedStep = currentStep.copy(status = errorResponse.status, response = Some(errorResponse.response))
              executionRepository.updateExecutionStep(executionId, currentStep.id.get, updatedStep).map { _ =>
                publishStepUpdate(executionId, errorResponse.id, s"Step failed: ${e.getMessage}", StepStatus.ERROR)
                stepResults += (currentStep.id.get -> errorResponse)
                backgroundSteps -= currentStep.id.get
              }
          }

          // Continue processing next steps immediately
          processStepsSequentially(nextSteps)
        } else {
          // Execute regular step and wait for it to complete before proceeding
          executeStep(currentStep).flatMap { response =>
            val updatedStep = currentStep.copy(status = response.status, response = Some(response.response))
            executionRepository.updateExecutionStep(executionId, currentStep.id.get, updatedStep).flatMap { _ =>
              publishStepUpdate(executionId, response.id, "Step completed", response.status, Some(response.response))
              stepResults += (currentStep.id.get -> response)

              if (response.status == StepStatus.ERROR && !currentStep.continueOnSuccess) {
                logger.error(s"Step ${currentStep.name} failed with error status, stopping flow")
                Future.successful(false)
              } else {
                // Continue with next steps
                processStepsSequentially(nextSteps)
              }
            }
          }.recoverWith {
            case e: Exception =>
              logger.error(s"Step ${currentStep.name} failed: ${e.getMessage}", e)

              val errorResponse = StepResponse(
                name = currentStep.name,
                id = currentStep.id.getOrElse(""),
                status = StepStatus.ERROR,
                response = StepError(
                  error = e.getMessage,
                  expectedValue = None,
                  actualValue = None
                )
              )

              val updatedStep = currentStep.copy(status = errorResponse.status, response = Some(errorResponse.response))
              executionRepository.updateExecutionStep(executionId, currentStep.id.get, updatedStep).flatMap { _ =>
                publishStepUpdate(executionId, errorResponse.id, s"Step failed: ${e.getMessage}", StepStatus.ERROR)
                stepResults += (currentStep.id.get -> errorResponse)

                if (!currentStep.continueOnSuccess) {
                  // Stop flow execution on error
                  Future.successful(false)
                } else {
                  // Continue with next steps despite error
                  logger.warn(s"Step ${currentStep.name} failed but continueOnSuccess=true, continuing flow")
                  processStepsSequentially(nextSteps)
                }
              }
          }
        }
      }
    }

    // Start processing steps sequentially
    processStepsSequentially(steps)
  }

  /**
   * Convert ExecutionStep to FlowStep for processing
   */
  private def convertExecutionStepToFlowStep(executionStep: ExecutionStep): FlowStep = {
    FlowStep(
      id = executionStep.id,
      name = executionStep.name,
      stepType = executionStep.stepType,
      meta = executionStep.meta,
      timeoutMs = executionStep.timeoutMs,
      runInBackground = executionStep.runInBackground,
      continueOnSuccess = executionStep.continueOnSuccess
    )
  }

  /**
   * Publish execution status update to Redis
   */
  private def publishExecutionUpdate(executionId: String, message: String, status: ExecutionStatus): Unit = {
    Try {
      val update = ExecutionStatusUpdate(
        executionId = executionId,
        updateType = ab.async.tester.domain.enums.ExecutionUpdateType.MESSAGE,
        stepUpdate = None,
        message = Some(message),
        executionStatus = status
      )

      val jedis = redisClient.getPool.getResource
      try {
        val result = jedis.publish(redisChannel, update.asJson.noSpaces)
        logger.info(s"Published execution update for $executionId: $message and result $result")
      } finally {
        jedis.close()
      }
    }.recover {
      case e: Exception =>
        logger.error(s"Failed to publish execution update for $executionId: ${e.getMessage}", e)
    }
  }

  /**
   * Publish step status update to Redis
   */
  private def publishStepUpdate(executionId: String, stepId: String, message: String, status: StepStatus, stepResponse: Option[StepResponseValue] = None): Unit = {
    Try {
      val stepUpdate = StepUpdate(
        stepId = stepId,
        status = status,
        response = stepResponse
      )

      val update = ExecutionStatusUpdate(
        executionId = executionId,
        updateType = ab.async.tester.domain.enums.ExecutionUpdateType.STEP_UPDATE,
        stepUpdate = Some(stepUpdate),
        message = Some(message),
        executionStatus = ExecutionStatus.InProgress // Default for step updates
      )

      val jedis = redisClient.getPool.getResource
      try {
        jedis.publish(redisChannel, update.asJson.noSpaces)
        logger.debug(s"Published step update for $executionId/$stepId: $message: $status")
      } finally {
        jedis.close()
      }
    }.recover {
      case e: Exception =>
        logger.error(s"Failed to publish step update for $executionId/$stepId: ${e.getMessage}", e)
    }
  }
}