package ab.async.tester.workers.app.runner

import ab.async.tester.domain.execution.ExecutionStatusUpdate
import ab.async.tester.domain.flow.Floww
import ab.async.tester.library.repository.execution.ExecutionRepository
import akka.actor.ActorRef
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{CompletionStrategy, OverflowStrategy}
import com.google.inject.{ImplementedBy, Inject, Singleton}
import org.reactivestreams.Publisher
import play.api.Logger

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

/**
 * Flow runner trait
 */
@ImplementedBy(classOf[FlowRunnerImpl])
trait FlowRunner {
  /**
   * Run a flow with steps
   *
   * @param flow the flow to run
   * @return a Source of status updates from the flow execution
   */
  def runFlow(flow: Floww): Source[ExecutionStatusUpdate, akka.NotUsed]
}

/**
 * Manages flow step execution
 */
@Singleton
class FlowRunnerImpl @Inject()(
  executionRepository: ExecutionRepository,
  stepRunnerRegistry: StepRunnerRegistry
)(implicit system: akka.actor.ActorSystem, ec: ExecutionContext) extends FlowRunner {
  
  private implicit val logger: Logger = Logger(this.getClass)
  private val runnerName = "FlowRunner"
  
  /**
   * Run a flow with steps
   */
  override def runFlow(flow: Floww): Source[ExecutionStatusUpdate, akka.NotUsed] = {
    // Define completion and failure matchers with explicit parameter types
    val completionMatcher: PartialFunction[Any, CompletionStrategy] = {
      case msg: ExecutionStatusUpdate if msg.`type` == StatusUpdateType.Complete => 
        CompletionStrategy.draining
    }
    
    val failureMatcher: PartialFunction[Any, Exception] = {
      case msg: ExecutionStatusUpdate if msg.`type` == StatusUpdateType.Error => 
        new Exception("Flow execution error")
    }
    
    // Using Source.actorRef with explicit typing
    val (actorRef: ActorRef, publisher: Publisher[ExecutionStatusUpdate]) = Source
      .actorRef[ExecutionStatusUpdate](
        completionMatcher,
        failureMatcher,
        bufferSize = 100,
        overflowStrategy = OverflowStrategy.backpressure
      )
      .toMat(Sink.asPublisher(false))(Keep.both)
      .run()

    // Execute the flow in the background
    MetricUtils.withAsyncServiceMetrics(runnerName, "runFlow") {
      executeFlow(flow, actorRef)
    }.recover {
      case e: Exception =>
        logger.error(s"Error executing flow ${flow.id.getOrElse("unknown")}: ${e.getMessage}", e)
        actorRef ! ExecutionStatusUpdate.createError("Flow execution failed: " + e.getMessage, "FLOW_EXEC_ERROR")
    }

    // Return the source from the publisher with proper typing
    Source.fromPublisher(publisher)
  }
  
  /**
   * Execute a flow asynchronously
   */
  private def executeFlow(flow: Floww, statusChannel: ActorRef): Future[Unit] = {
    // Save execution first
    executionRepository.saveExecution(flow).flatMap { execution =>
      val executionId = execution.id.getOrElse("unknown")
      
      statusChannel ! ExecutionStatusUpdate.createInfo(s"Starting flow execution with ID: $executionId")
      
      // Update status to in-progress
      executionRepository.updateStatus(executionId, ExecutionStatus.InProgress).map { _ =>
        // Execute steps with background support
        val steps = flow.steps
        executeFlowSteps(steps, statusChannel).map { successful =>
          // Update execution status based on result
          val finalStatus = if (successful) ExecutionStatus.Completed else ExecutionStatus.Failed
          executionRepository.updateStatus(executionId, finalStatus)

          // Send completion message
          val message = if (successful) s"Flow execution completed successfully" else "Flow execution failed"
          statusChannel ! ExecutionStatusUpdate.createComplete(message)
        }
      }
    }
  }
  
  /**
   * Execute steps sequentially in order with background execution support
   */
  private def executeFlowSteps(steps: List[FlowStep], statusChannel: ActorRef): Future[Boolean] = {
    val promise = Promise[Boolean]()
    
    // Tracks results of all completed steps by name
    val stepResults = MutableMap.empty[String, StepResponse]
    
    // Tracks currently running background steps by name
    val backgroundSteps = MutableMap.empty[String, Future[StepResponse]]

    // Execute a single step and handle background behavior
    def executeStep(step: FlowStep): Future[StepResponse] = {
      logger.info(s"Executing step ${step.name} (background: ${step.runInBackground})")

      // Find the appropriate runner for this step
      val runner = stepRunnerRegistry.getRunnerForStep(step.stepType)

      // Execute the step with the current results
      val allResults = stepResults.values.toList
      runner.runStep(step, allResults)
    }
    
    // Execute steps sequentially
    def processStepsSequentially(remainingSteps: List[FlowStep]): Future[Boolean] = {
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
          backgroundSteps += (currentStep.name -> stepFuture)
          
          // Handle background step completion
          stepFuture.onComplete {
            case Success(response) => {
              statusChannel ! ExecutionStatusUpdate.createStepUpdate(response)
              stepResults += (currentStep.name -> response)
              backgroundSteps -= currentStep.name
              
              if (response.status == StepStatus.ERROR && !currentStep.continueOnSuccess) {
                logger.error(s"Background step ${currentStep.name} failed with error status")
                promise.trySuccess(false)
              }
            }
            case Failure(e) => {
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
              
              statusChannel ! ExecutionStatusUpdate.createStepUpdate(errorResponse)
              stepResults += (currentStep.name -> errorResponse)
              backgroundSteps -= currentStep.name
              
              if (!currentStep.continueOnSuccess) {
                promise.trySuccess(false)
              }
            }
          }
          
          // Continue processing next steps immediately
          processStepsSequentially(nextSteps)
        } else {
          // Execute regular step and wait for it to complete before proceeding
          executeStep(currentStep).flatMap { response =>
            statusChannel ! ExecutionStatusUpdate.createStepUpdate(response)
            stepResults += (currentStep.name -> response)
            
            if (response.status == StepStatus.ERROR && !currentStep.continueOnSuccess) {
              logger.error(s"Step ${currentStep.name} failed with error status, stopping flow")
              Future.successful(false)
            } else {
              // Continue with next steps
              processStepsSequentially(nextSteps)
            }
          }.recoverWith {
            case e: Exception => {
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
              
              statusChannel ! ExecutionStatusUpdate.createStepUpdate(errorResponse)
              stepResults += (currentStep.name -> errorResponse)
              
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
    processStepsSequentially(steps).onComplete {
      case Success(result) => promise.success(result)
      case Failure(e) => {
        logger.error(s"Error in flow execution: ${e.getMessage}", e)
        promise.success(false)
      }
    }
    
    promise.future
  }
} 