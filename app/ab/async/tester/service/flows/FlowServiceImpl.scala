package ab.async.tester.service.flows

import ab.async.tester.constants.StepFunctions
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.models.flow.Floww
import ab.async.tester.models.requests.flow.GetFlowsRequest
import ab.async.tester.models.step.{FlowStep, StepError, StepResponse}
import ab.async.tester.models.enums.StepStatus
import ab.async.tester.models.execution.ExecutionStatusUpdate
import ab.async.tester.repository.flow.FlowRepository
import ab.async.tester.runners.FlowRunner
import ab.async.tester.utils.MetricUtils
import akka.NotUsed
import akka.stream.scaladsl.Source
import com.google.inject.Singleton
import play.api.Logger

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class FlowServiceImpl @Inject()(
  flowRepository: FlowRepository,
  flowRunner: FlowRunner
)(implicit ec: ExecutionContext) extends FlowServiceTrait {
  private implicit val logger: Logger = Logger(this.getClass)
  private val serviceName = "FlowService"
  
  /**
   * Runs a flow asynchronously
   * 
   * @param flow flow to run
   * @return source of status updates
   */
  override def runFlow(flow: Floww): Source[ExecutionStatusUpdate, NotUsed] = {
    MetricUtils.withServiceMetrics[Source[ExecutionStatusUpdate, NotUsed]](serviceName, "runFlow") {
      logger.info(s"Running flow: ${flow.id.getOrElse("new")} - ${flow.name}")
      
      try {
        // Validate steps including dependencies before running
        validateSteps(flow)
        
        // Run the flow
        flowRunner.runFlow(flow)
      } catch {
        case e: ValidationException =>
          logger.error(s"Validation error when running flow: ${e.getMessage}")
          Source.single(ExecutionStatusUpdate.createError(s"Validation error: ${e.getMessage}", "VALIDATION_ERROR"))
        case e: Exception =>
          logger.error(s"Error running flow: ${e.getMessage}", e)
          Source.single(ExecutionStatusUpdate.createError(s"Error running flow: ${e.getMessage}", "FLOW_ERROR"))
      }
    }
  }
  
  /**
   * Validates the steps of a flow
   * 
   * @param flow flow to validate
   * @throws ValidationException if validation fails
   */
  override def validateSteps(flow: Floww): Unit = {
    MetricUtils.withServiceMetrics(serviceName, "validateSteps") {
      if (flow.steps.isEmpty) {
        throw ValidationException("Flow must have at least one step")
      }
      
      // Check for step name uniqueness
      val stepNames = flow.steps.map(_.name)
      if (stepNames.length != stepNames.distinct.length) {
        val duplicates = stepNames.groupBy(identity).filter(_._2.size > 1).keys
        throw ValidationException(s"Duplicate step names found: ${duplicates.mkString(", ")}")
      }
      
      // Validate step types
      flow.steps.foreach { step =>
        validateStepFunction(step)
      }
    }
  }

  /**
   * Validates the function of a step
   * 
   * @param step the step to validate
   * @throws ValidationException if validation fails
   */
  private def validateStepFunction(step: FlowStep): Unit = {
    if (step.function.isEmpty) {
      throw ValidationException(s"Step ${step.name} is missing a function")
    }
    
    val lowerFunction = step.function.toLowerCase()
    if (!StepFunctions.ALL_STEP_FUNCTIONS.contains(lowerFunction)) {
      throw ValidationException(s"Step ${step.name} has an unknown function type: ${step.function}")
    }
  }
  
  /**
   * Adds a new flow
   * 
   * @param flow flow to add
   * @return the added flow with id
   */
  override def addFlow(flow: Floww): Future[Floww] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "addFlow") {
      try {
        // Validate steps 
        validateSteps(flow)
        
        // Check if there's an existing flow with the same name
        // We do this to prevent duplicate flows with the same name
        flowRepository.findByName(flow.name).flatMap {
          case Some(existingFlow) =>
            // Return the existing flow with a message
            Future.successful(existingFlow)
          case None =>
            // Create new flow
            val now = System.currentTimeMillis() / 1000
            val newFlow = flow.copy(
              createdAt = now,
              modifiedAt = now
            )
            
            flowRepository.insert(newFlow).map { createdFlow =>
              logger.info(s"Created new flow: ${createdFlow.id.getOrElse("")} - ${createdFlow.name}")
              createdFlow
            }
        }
      } catch {
        case e: ValidationException =>
          logger.error(s"Validation error when adding flow: ${e.getMessage}")
          Future.failed(e)
        case e: Exception =>
          logger.error(s"Error adding flow: ${e.getMessage}", e)
          Future.failed(e)
      }
    }
  }
  
  /**
   * Gets flows based on filter criteria
   * 
   * @param request filter criteria
   * @return list of matching flows
   */
  override def getFlows(request: GetFlowsRequest): Future[List[Floww]] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "getFlows") {
      flowRepository.findAll(request).recover {
        case e: Exception =>
          logger.error(s"Error retrieving flows: ${e.getMessage}", e)
          List.empty[Floww]
      }
    }
  }
  
  /**
   * Gets a single flow by ID
   * 
   * @param id the flow ID
   * @return the flow if found
   */
  override def getFlow(id: String): Future[Option[Floww]] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "getFlow") {
      flowRepository.findById(id).recover {
        case e: Exception =>
          logger.error(s"Error retrieving flow $id: ${e.getMessage}", e)
          None
      }
    }
  }
  
  /**
   * Updates an existing flow
   * 
   * @param flow the flow to update
   * @return true if successful
   */
  override def updateFlow(flow: Floww): Future[Boolean] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "updateFlow") {
      try {
        validateSteps(flow)
        
        val now = System.currentTimeMillis() / 1000
        val updatedFlow = flow.copy(modifiedAt = now)
        
        flowRepository.update(updatedFlow).map { success =>
          if (success) {
            logger.info(s"Flow updated: ${flow.id.getOrElse("")}")
          } else {
            logger.warn(s"Flow update failed: ${flow.id.getOrElse("")}")
          }
          success
        }
      } catch {
        case e: ValidationException =>
          logger.error(s"Validation error when updating flow: ${e.getMessage}")
          Future.failed(e)
        case e: Exception =>
          logger.error(s"Error updating flow: ${e.getMessage}", e)
          Future.failed(e)
      }
    }
  }
} 