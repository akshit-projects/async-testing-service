package ab.async.tester.service.flows

import ab.async.tester.domain.flow.{Floww, FlowVersion}
import ab.async.tester.domain.step.FlowStep
import com.google.inject.ImplementedBy

import scala.concurrent.Future
@ImplementedBy(classOf[FlowServiceImpl])
trait FlowServiceTrait {

  /** Validates the steps of a flow, throws ValidationException if invalid */
  def validateSteps(steps: List[FlowStep]): Unit

  /** Gets flows based on filter criteria */
  def getFlows(search: Option[String], flowIds: Option[List[String]], limit: Int, page: Int): Future[List[Floww]]

  /** Gets a single flow by ID */
  def getFlow(id: String): Future[Option[Floww]]

  /** Gets a flow with its latest version */
  def getFlowWithLatestVersion(id: String): Future[Option[(Floww, FlowVersion)]]

  /** Gets all versions of a flow */
  def getFlowVersions(flowId: String): Future[List[FlowVersion]]

  /** Gets a specific version of a flow */
  def getFlowVersion(flowId: String, version: Int): Future[Option[FlowVersion]]

  /** Adds a new flow with its first version */
  def addFlow(flow: Floww, steps: List[FlowStep]): Future[(Floww, FlowVersion)]

  /** Updates an existing flow by creating a new version */
  def updateFlow(flowId: String, steps: List[FlowStep], updatedBy: String, description: Option[String] = None): Future[Option[FlowVersion]]
}