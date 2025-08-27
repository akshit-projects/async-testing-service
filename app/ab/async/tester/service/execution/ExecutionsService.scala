package ab.async.tester.service.execution

import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.execution.Execution
import com.google.inject.ImplementedBy

import scala.concurrent.Future

@ImplementedBy(classOf[ExecutionsServiceImpl])
trait ExecutionsService {

  def getExecutionById(executionId: String): Future[Option[Execution]]

  def getExecutions(pageNumber: Int, pageSize: Int, statuses: Option[List[ExecutionStatus]]): Future[List[Execution]]

}
