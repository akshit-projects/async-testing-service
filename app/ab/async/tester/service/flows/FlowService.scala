package ab.async.tester.service.flows

import ab.async.tester.domain.common.PaginatedResponse
import ab.async.tester.domain.execution.Execution
import ab.async.tester.domain.flow.{FlowVersion, Floww}
import ab.async.tester.domain.requests.RunFlowRequest
import com.google.inject.ImplementedBy

import scala.concurrent.Future
@ImplementedBy(classOf[FlowServiceImpl])
trait FlowService {

  /** Creates execution and publishes to Kafka for workers to pick up. Returns
    * execution details without streaming.
    */
  def createExecution(runRequest: RunFlowRequest): Future[Execution]

  def exportFlows(
      flowIds: Option[List[String]],
      orgId: Option[String],
      teamId: Option[String]
  ): Future[List[Floww]]

  def importFlows(flows: List[Floww], creatorId: String): Future[List[Floww]]

  /** Validates the steps of a flow, throws ValidationException if invalid */
  def validateFlow(flow: Floww): Unit

  /** Gets flows based on filter criteria with pagination */
  def getFlows(
      search: Option[String],
      flowIds: Option[List[String]],
      orgId: Option[String],
      teamId: Option[String],
      stepTypes: Option[List[String]],
      limit: Int,
      page: Int
  ): Future[PaginatedResponse[Floww]]

  /** Gets a single flow by ID */
  def getFlow(id: String): Future[Option[Floww]]

  /** Gets all versions of a flow with pagination */
  def getFlowVersions(
      flowId: String,
      limit: Int,
      page: Int
  ): Future[PaginatedResponse[FlowVersion]]

  /** Gets a specific version of a flow */
  def getFlowVersion(flowId: String, version: Int): Future[Option[FlowVersion]]

  /** Adds a new flow */
  def addFlow(flow: Floww): Future[Floww]

  /** Updates an existing flow */
  def updateFlow(flow: Floww): Future[Boolean]
}
