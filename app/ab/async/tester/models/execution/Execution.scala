package ab.async.tester.models.execution

import ab.async.tester.models.flow.Floww
import ab.async.tester.models.step.FlowStep
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

/**
 * Represents a flow execution record
 */
case class Execution(
  id: Option[String] = None,
  executionFlow: ExecutionFlow,
  status: ExecutionStatus,
  totalTimeout: Int,
  createdAt: Long = System.currentTimeMillis() / 1000,
  modifiedAt: Long = System.currentTimeMillis() / 1000
)

/**
 * Simplified flow for execution
 */
case class ExecutionFlow(
  id: Option[String],
  name: Option[String],
  steps: List[FlowStep]
)

object ExecutionFlow {
  implicit val encoder: Encoder[ExecutionFlow] = deriveEncoder
  implicit val decoder: Decoder[ExecutionFlow] = deriveDecoder
  
  def fromFlow(flow: Floww): ExecutionFlow = {
    ExecutionFlow(
      id = flow.id,
      name = Option(flow.name),
      steps = flow.steps
    )
  }
}

object Execution {
  implicit val encoder: Encoder[Execution] = deriveEncoder
  implicit val decoder: Decoder[Execution] = deriveDecoder
  
  def fromFlow(flow: Floww): Execution = {
    val execFlow = ExecutionFlow.fromFlow(flow)
    val totalTimeout = flow.steps.map(_.timeout).sum + 100 // Add 100ms buffer for communication
    
    Execution(
      executionFlow = execFlow,
      status = ExecutionStatus.Todo,
      totalTimeout = totalTimeout
    )
  }
} 