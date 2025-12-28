package ab.async.tester.service.testsuite

import ab.async.tester.domain.common.PaginatedResponse
import ab.async.tester.domain.requests.RunTestSuiteRequest
import ab.async.tester.domain.testsuite.{TestSuite, TestSuiteExecution}
import ab.async.tester.domain.enums.ExecutionStatus
import com.google.inject.ImplementedBy

import scala.concurrent.Future

/** Service for test suite operations
  */
@ImplementedBy(classOf[TestSuiteServiceImpl])
trait TestSuiteService {

  /** Get all test suites with optional filtering and pagination
    */
  def getTestSuites(
      search: Option[String],
      creator: Option[String],
      enabled: Option[Boolean],
      orgId: Option[String],
      teamId: Option[String],
      limit: Int,
      page: Int
  ): Future[PaginatedResponse[TestSuite]]

  /** Get a single test suite by ID
    */
  def getTestSuite(id: String): Future[Option[TestSuite]]

  /** Create a new test suite
    */
  def createTestSuite(testSuite: TestSuite): Future[TestSuite]

  /** Update an existing test suite
    */
  def updateTestSuite(testSuite: TestSuite): Future[Boolean]

  /** Delete a test suite
    */
  def deleteTestSuite(id: String): Future[Boolean]

  /** Trigger execution of a test suite
    */
  def triggerTestSuite(request: RunTestSuiteRequest): Future[TestSuiteExecution]

  /** Get test suite executions with pagination
    */
  def getTestSuiteExecutions(
      testSuiteId: Option[String],
      limit: Int,
      page: Int,
      statuses: Option[List[ExecutionStatus]]
  ): Future[PaginatedResponse[TestSuiteExecution]]

  /** Get a specific test suite execution
    */
  def getTestSuiteExecution(id: String): Future[Option[TestSuiteExecution]]

  /** Validate test suite configuration
    */
  def validateTestSuite(testSuite: TestSuite): Future[Unit]
}
