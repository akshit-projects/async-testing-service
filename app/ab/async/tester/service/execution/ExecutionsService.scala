package ab.async.tester.service.execution

import ab.async.tester.domain.common.PaginatedResponse
import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.execution.Execution
import akka.NotUsed
import akka.stream.scaladsl.Source
import com.google.inject.ImplementedBy

import scala.concurrent.Future

@ImplementedBy(classOf[ExecutionsServiceImpl])
trait ExecutionsService {

  def getExecutionById(executionId: String): Future[Option[Execution]]

  def getExecutions(pageNumber: Int, pageSize: Int, statuses: Option[List[ExecutionStatus]]): Future[PaginatedResponse[Execution]]

  /**
   * Creates a stream for execution updates by subscribing to Redis.
   * Returns a Source that emits execution updates as JSON strings.
   */
  def streamExecutionUpdates(executionId: String, clientId: Option[String]): Source[String, NotUsed]

  /**
   * Called when WebSocket connection terminates to clean up resources.
   */
  def stopExecutionStream(executionId: String): Unit

}
