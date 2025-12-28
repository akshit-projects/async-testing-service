package ab.async.tester.service.execution

import ab.async.tester.domain.common.{PaginatedResponse, PaginationMetadata}
import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.execution.Execution
import ab.async.tester.library.clients.redis.RedisPubSubService
import ab.async.tester.library.repository.execution.ExecutionRepository
import akka.NotUsed
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.Source
import com.google.inject.{Inject, Singleton}
import io.circe.Json

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ExecutionsServiceImpl @Inject() (
    executionRepository: ExecutionRepository,
    redisPubSubService: RedisPubSubService
)(implicit mat: Materializer, ec: ExecutionContext)
    extends ExecutionsService {

  def getExecutionById(executionId: String): Future[Option[Execution]] = {
    executionRepository.findById(executionId)
  }

  def getExecutions(
      pageNumber: Int,
      pageSize: Int,
      statuses: Option[List[ExecutionStatus]]
  ): Future[PaginatedResponse[Execution]] = {
    executionRepository.getExecutions(pageNumber, pageSize, statuses).map {
      case (executions, total) =>
        PaginatedResponse(
          data = executions,
          pagination = PaginationMetadata(pageNumber, pageSize, total)
        )
    }
  }

  override def streamExecutionUpdates(
      executionId: String,
      clientId: Option[String]
  ): Source[String, NotUsed] = {
    val (queue, source) =
      Source.queue[Json](64, OverflowStrategy.dropHead).preMaterialize()

    // Register queue for Redis updates
    redisPubSubService.registerQueue(executionId, clientId, queue)

    // Map Json to String for WebSocket
    source.map(_.noSpaces)
  }

  override def stopExecutionStream(executionId: String): Unit = {
    redisPubSubService.unregisterQueue(executionId)
  }

}
