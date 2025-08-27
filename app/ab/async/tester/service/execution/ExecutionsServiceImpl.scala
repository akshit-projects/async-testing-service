package ab.async.tester.service.execution

import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.execution.Execution
import ab.async.tester.library.repository.execution.ExecutionRepository
import com.google.inject.{Inject, Singleton}

import scala.concurrent.Future

@Singleton
class ExecutionsServiceImpl @Inject()(executionRepository: ExecutionRepository) extends ExecutionsService {

  def getExecutionById(executionId: String): Future[Option[Execution]] = {
    executionRepository.findById(executionId)
  }

  def getExecutions(pageNumber: Int, pageSize: Int, statuses: Option[List[ExecutionStatus]]): Future[List[Execution]] = {
    executionRepository.getExecutions(pageNumber, pageSize, statuses)
  }

}
