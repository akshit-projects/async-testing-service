package ab.async.tester.service.flows
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
   * Starts execution for a given RunRequest.
   * Returns a SourceQueue which will emit execution updates as JSON to be streamed to the client.
   */
  def startExecution(runRequest: RunFlowRequest): Future[ExecutionStreams]

  /**
   * Called when WebSocket connection terminates to clean up resources.
   */
  def stopExecution(executionId: String): Unit
}