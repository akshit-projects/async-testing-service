package ab.async.tester.service.flows

import ab.async.tester.models.execution.ExecutionStatusUpdate
import ab.async.tester.models.flow.Floww
import ab.async.tester.models.requests.flow.GetFlowsRequest
import akka.NotUsed
import akka.stream.scaladsl.Source
import com.google.inject.ImplementedBy

import scala.concurrent.Future

/**
 * Service interface for flow operations
 */
@ImplementedBy(classOf[FlowServiceImpl])
trait FlowServiceTrait {
  /**
   * Runs a flow asynchronously
   *
   * @param flow flow to run
   * @return source of status updates
   */
  def runFlow(flow: Floww): Source[ExecutionStatusUpdate, NotUsed]
  
  /**
   * Validates the steps of a flow
   *
   * @param flow flow to validate
   * @throws ValidationException if validation fails
   */
  def validateSteps(flow: Floww): Unit
  
  /**
   * Gets flows based on filter criteria
   *
   * @param request filter criteria
   * @return list of matching flows
   */
  def getFlows(getFlowsRequest: GetFlowsRequest): Future[List[Floww]]
  
  /**
   * Gets a single flow by ID
   *
   * @param id the flow ID
   * @return the flow if found
   */
  def getFlow(id: String): Future[Option[Floww]]
  
  /**
   * Adds a new flow
   *
   * @param flow flow to add
   * @return the added flow with id
   */
  def addFlow(flow: Floww): Future[Floww]
  
  /**
   * Updates an existing flow
   *
   * @param flow the flow to update
   * @return true if successful
   */
  def updateFlow(flow: Floww): Future[Boolean]
}
