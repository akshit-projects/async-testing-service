package ab.async.tester.service.flows

import ab.async.tester.domain.execution.Execution
import ab.async.tester.domain.requests.RunFlowRequest
import akka.NotUsed
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}
import io.circe.Json

import scala.concurrent.Future

case class ExecutionStreams(
                             queue: SourceQueueWithComplete[Json],
                             source: Source[String, NotUsed],
                             executionId: String
                           )

trait FlowExecutionService {
  /**
   * Creates execution and publishes to Kafka for workers to pick up.
   * Returns execution details without streaming.
   */
  def createExecution(runRequest: RunFlowRequest): Future[Execution]

  /**
   * Creates a stream for execution updates by subscribing to Redis.
   * Returns a Source that emits execution updates as JSON strings.
   */
  def streamExecutionUpdates(executionId: String): Source[String, NotUsed]

  /**
   * Called when WebSocket connection terminates to clean up resources.
   */
  def stopExecutionStream(executionId: String): Unit

}