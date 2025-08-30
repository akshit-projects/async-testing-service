package ab.async.tester.library.repository.testsuite

import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.testsuite.{TestSuiteExecution, TestSuiteFlowExecution}
import ab.async.tester.library.constants.Constants.COMPLETED_EXECUTIONS
import ab.async.tester.library.repository.execution.ExecutionTable.executionStatusColumnType
import ab.async.tester.library.utils.{DecodingUtils, MetricUtils}
import com.google.inject.{ImplementedBy, Inject, Singleton}
import io.circe.syntax.EncoderOps
import play.api.Logger
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.jdbc.PostgresProfile.api._

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/**
 * Repository for test suite execution persistence
 */
@ImplementedBy(classOf[TestSuiteExecutionRepositoryImpl])
trait TestSuiteExecutionRepository {
  def findById(id: String): Future[Option[TestSuiteExecution]]
  def findByTestSuiteId(testSuiteId: String, limit: Int, page: Int): Future[List[TestSuiteExecution]]
  def findAll(limit: Int, page: Int, statuses: Option[List[ExecutionStatus]]): Future[List[TestSuiteExecution]]
  def insert(testSuiteExecution: TestSuiteExecution): Future[TestSuiteExecution]
  def update(testSuiteExecution: TestSuiteExecution): Future[Boolean]
  def updateTestSuiteExecution(testSuiteExecutionId: String, executionId: String, executionStatus: ExecutionStatus): Future[Unit]
  def updateStatus(id: String, status: ExecutionStatus): Future[Boolean]
}

@Singleton
class TestSuiteExecutionRepositoryImpl @Inject()(
  db: Database
)(implicit ec: ExecutionContext) extends TestSuiteExecutionRepository {

  private implicit val logger: Logger = Logger(this.getClass)
  private val repositoryName = "TestSuiteExecutionRepository"

  class TestSuiteExecutionTable(tag: Tag) extends Table[TestSuiteExecution](tag, "test_suite_executions") {
    implicit val flowExecutionsColumnType: JdbcType[List[TestSuiteFlowExecution]] with BaseTypedType[List[TestSuiteFlowExecution]] = 
      MappedColumnType.base[List[TestSuiteFlowExecution], String](
        flowExecs => flowExecs.asJson.noSpaces,
        str => DecodingUtils.decodeWithErrorLogs[List[TestSuiteFlowExecution]](str)
      )

    implicit val instantColumnType: BaseColumnType[Instant] =
      MappedColumnType.base[Instant, java.sql.Timestamp](
        inst => java.sql.Timestamp.from(inst),
        ts   => ts.toInstant
      )

    def id = column[String]("id", O.PrimaryKey)
    def testSuiteId = column[String]("testsuiteid")
    def testSuiteName = column[String]("testsuitename")
    def status = column[ExecutionStatus]("status")
    def startedAt = column[Instant]("startedat")
    def completedAt = column[Option[Instant]]("completedat")
    def flowExecutions = column[List[TestSuiteFlowExecution]]("flowexecutions")
    def runUnordered = column[Boolean]("rununordered")
    def triggeredBy = column[String]("triggeredby")
    def * = (id, testSuiteId, testSuiteName, status, startedAt, completedAt, flowExecutions, runUnordered, triggeredBy) <> (
      {
        case (id, testSuiteId, testSuiteName, status, startedAt, completedAt, flowExecutions, runUnordered, triggeredBy) =>
          TestSuiteExecution(id, testSuiteId, testSuiteName, status, startedAt, completedAt, flowExecutions, runUnordered, triggeredBy)
      },
      (tse: TestSuiteExecution) => {
        Some((tse.id, tse.testSuiteId, tse.testSuiteName, tse.status, tse.startedAt, tse.completedAt, tse.flowExecutions, tse.runUnordered, tse.triggeredBy))
      }
    )
  }

  private val testSuiteExecutions = TableQuery[TestSuiteExecutionTable]

  override def findById(id: String): Future[Option[TestSuiteExecution]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findById") {
      db.run(testSuiteExecutions.filter(_.id === id).result.headOption)
    }
  }

  override def findByTestSuiteId(testSuiteId: String, limit: Int, page: Int): Future[List[TestSuiteExecution]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findByTestSuiteId") {
      val query = testSuiteExecutions
        .filter(_.testSuiteId === testSuiteId)
//        .sortBy(_.startedAt.desc)
        .drop(page * limit)
        .take(limit)
      
      db.run(query.result).map(_.toList)
    }
  }

  override def findAll(limit: Int, page: Int, statuses: Option[List[ExecutionStatus]]): Future[List[TestSuiteExecution]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findAll") {
      val baseQuery = statuses match {
        case Some(s) if s.nonEmpty =>
          testSuiteExecutions.filter(_.status.inSet(s))
        case _ =>
          testSuiteExecutions
      }

      val query = baseQuery
//        .sortBy(_.startedAt.desc)
        .drop(page * limit)
        .take(limit)

      println(query.result.statements.toString)
      db.run(query.result).map(_.toList)
    }
  }

  override def insert(testSuiteExecution: TestSuiteExecution): Future[TestSuiteExecution] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "insert") {
      db.run(testSuiteExecutions += testSuiteExecution).map(_ => testSuiteExecution)
    }
  }

  override def update(testSuiteExecution: TestSuiteExecution): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "update") {
      db.run(testSuiteExecutions.filter(_.id === testSuiteExecution.id).update(testSuiteExecution)).map(_ > 0)
    }
  }

  override def updateStatus(id: String, status: ExecutionStatus): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updateStatus") {
//      val completedAt = if (isCompleted) Some(Instant.now()) else None
      
      val updateQuery = testSuiteExecutions
        .filter(_.id === id)
//        .map(tse => (tse.status, tse.completedFlows, tse.failedFlows, tse.completedAt))
//        .update((status, completedFlows, failedFlows, completedAt))
      
      db.run(updateQuery.result).map(_.nonEmpty)
    }
  }

  override def updateTestSuiteExecution(testSuiteExecutionId: String, executionId: String, executionStatus: ExecutionStatus): Future[Unit] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updateTestSuiteExecution") {
      for {
        existingSuite <- db.run(testSuiteExecutions.filter(_.id === testSuiteExecutionId).result.headOption)
        updatedSuite <- {
          val suite = existingSuite.get
          val matchingExecutionIndex = suite.flowExecutions.indexWhere(_.executionId == executionId)
          val testSuiteExecution = if (COMPLETED_EXECUTIONS.contains(executionStatus)) {
            suite.flowExecutions(matchingExecutionIndex).copy(status = executionStatus)
          } else {
            suite.flowExecutions(matchingExecutionIndex).copy(status = executionStatus, completedAt = Some(Instant.now()))
          }
          val updatedExecutions = suite.flowExecutions.updated(matchingExecutionIndex, testSuiteExecution)
          val areAllUpdated = areAllExecutionsUpdated(updatedExecutions)
          val updatedSuite = if (areAllUpdated) {
            suite.copy(flowExecutions = updatedExecutions, status = ExecutionStatus.Completed, completedAt = Some(Instant.now()))
          } else {
            suite.copy(flowExecutions = updatedExecutions)
          }
          update(updatedSuite)
        }
      } yield {

      }
    }
  }

  private def areAllExecutionsUpdated(flowExecutions: List[TestSuiteFlowExecution]) = {
    !flowExecutions.exists { execution =>
      COMPLETED_EXECUTIONS.contains(execution.status)
    }
  }
}
