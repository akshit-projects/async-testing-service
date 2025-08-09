package ab.async.tester.service.flows

import ab.async.tester.domain.flow.{Floww, FlowVersion}
import com.google.inject.ImplementedBy

import scala.concurrent.Future
@ImplementedBy(classOf[FlowServiceImpl])
trait FlowServiceTrait {

  /** Validates the steps of a flow, throws ValidationException if invalid */
  def validateSteps(flow: Floww): Unit

  /** Gets flows based on filter criteria */
  def getFlows(search: Option[String], flowIds: Option[List[String]], limit: Int, page: Int): Future[List[Floww]]

  /** Gets a single flow by ID */
  def getFlow(id: String): Future[Option[Floww]]

  /** Gets all versions of a flow */
  def getFlowVersions(flowId: String): Future[List[FlowVersion]]

  /** Gets a specific version of a flow */
  def getFlowVersion(flowId: String, version: Int): Future[Option[FlowVersion]]

  /** Adds a new flow */
  def addFlow(flow: Floww): Future[Floww]

  /** Updates an existing flow */
  def updateFlow(flow: Floww): Future[Boolean]
}