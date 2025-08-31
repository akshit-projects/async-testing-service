package ab.async.tester.service.testsuite

import ab.async.tester.domain.clients.kafka.KafkaConfig
import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.execution.Execution
import ab.async.tester.domain.requests.{RunFlowRequest, RunTestSuiteRequest}
import ab.async.tester.domain.testsuite.{TestSuite, TestSuiteExecution, TestSuiteFlowExecution}
import ab.async.tester.library.repository.flow.FlowRepository
import ab.async.tester.library.repository.testsuite.{TestSuiteExecutionRepository, TestSuiteRepository}
import ab.async.tester.service.flows.FlowService
import com.google.inject.{Inject, Singleton}
import com.typesafe.config.Config
import play.api.Configuration

import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/**
 * Implementation of TestSuiteService
 */
@Singleton
class TestSuiteServiceImpl @Inject()(
                                      testSuiteRepository: TestSuiteRepository,
                                      testSuiteExecutionRepository: TestSuiteExecutionRepository,
                                      flowRepository: FlowRepository,
                                      flowService: FlowService,
                                      configuration: Configuration
)(implicit ec: ExecutionContext) extends TestSuiteService {

  private val testSuiteExecutionTopic = configuration.get[String]("events.testSuiteExecutionTopic")
  private val kafkaConfig = {
    val conf = configuration.get[Config]("kafka")
    KafkaConfig(
      bootstrapServers = conf.getString("bootstrapServers"),
    )
  }

  override def getTestSuites(search: Option[String], creator: Option[String], enabled: Option[Boolean], limit: Int, page: Int): Future[List[TestSuite]] = {
    testSuiteRepository.findAll(search, creator, enabled, limit, page)
  }

  override def getTestSuite(id: String): Future[Option[TestSuite]] = {
    testSuiteRepository.findById(id)
  }

  override def createTestSuite(testSuite: TestSuite): Future[TestSuite] = {
    for {
      _ <- validateTestSuite(testSuite)
      created <- testSuiteRepository.insert(testSuite)
    } yield created
  }

  override def updateTestSuite(testSuite: TestSuite): Future[Boolean] = {
    for {
      _ <- validateTestSuite(testSuite)
      updated <- testSuiteRepository.update(testSuite)
    } yield updated
  }

  override def deleteTestSuite(id: String): Future[Boolean] = {
    testSuiteRepository.delete(id)
  }

  override def triggerTestSuite(request: RunTestSuiteRequest): Future[TestSuiteExecution] = {
    for {
      testSuiteOpt <- testSuiteRepository.findById(request.testSuiteId)
      testSuite <- testSuiteOpt match {
        case Some(ts) if ts.enabled => Future.successful(ts)
        case Some(_) => Future.failed(new IllegalArgumentException("Test suite is disabled"))
        case None => Future.failed(new IllegalArgumentException(s"Test suite not found: ${request.testSuiteId}"))
      }
      testSuiteExecution <- runTestSuite(testSuite, request)
      execution <- testSuiteExecutionRepository.insert(testSuiteExecution)
    } yield execution
  }

  private def runTestSuite(testSuite: TestSuite, request: RunTestSuiteRequest): Future[TestSuiteExecution] = {
    val testSuiteExecutionId = UUID.randomUUID().toString
    Future.sequence(testSuite.flows.map { flow =>
      val runFlowRequest = RunFlowRequest(
        flowId = flow.flowId,
        testSuiteExecutionId = Some(testSuiteExecutionId),
        // add initial delay to complete all processing of saving test suite
        params = request.globalParameters.getOrElse(Map.empty) ++ Map("initialDelay" -> Math.max(testSuite.flows.length * 100, 1000).toString)
      )
      flowService.createExecution(runFlowRequest)
    }).map { executions =>
      createTestSuiteExecution(testSuite, request, executions, testSuiteExecutionId)
    }
  }

  override def getTestSuiteExecutions(testSuiteId: Option[String], limit: Int, page: Int, statuses: Option[List[ExecutionStatus]]): Future[List[TestSuiteExecution]] = {
    testSuiteId match {
      case Some(tsId) => testSuiteExecutionRepository.findByTestSuiteId(tsId, limit, page)
      case None => testSuiteExecutionRepository.findAll(limit, page, statuses)
    }
  }

  override def getTestSuiteExecution(id: String): Future[Option[TestSuiteExecution]] = {
    testSuiteExecutionRepository.findById(id)
  }

  override def validateTestSuite(testSuite: TestSuite): Future[Unit] = {
    if (testSuite.flows.isEmpty) {
      Future.failed(new IllegalArgumentException("Test suite must contain at least one flow"))
    } else {
      // Validate that all referenced flows exist
      val flowValidations = testSuite.flows.map { flowConfig =>
        flowRepository.findById(flowConfig.flowId).map {
          case Some(_) => ()
          case None => throw new IllegalArgumentException(s"Flow not found: ${flowConfig.flowId}")
        }
      }
      Future.sequence(flowValidations).map(_ => ())
    }
  }

  private def createTestSuiteExecution(testSuite: TestSuite, request: RunTestSuiteRequest, executions: List[Execution], testSuiteExecutionId: String): TestSuiteExecution = {
    val now = Instant.now()
    val flowExecutions = executions.map { execution =>
      TestSuiteFlowExecution(
        flowId = execution.flowId,
        flowVersion = execution.flowVersion,
        executionId = execution.id,
        status = ExecutionStatus.Todo,
        startedAt = Instant.now(),
        parameters = request.globalParameters
      )
    }

    TestSuiteExecution(
      id = testSuiteExecutionId,
      testSuiteId = testSuite.id.get,
      testSuiteName = testSuite.name,
      status = ExecutionStatus.Todo,
      startedAt = now,
      flowExecutions = flowExecutions,
      runUnordered = testSuite.runUnordered,
      triggeredBy = request.triggeredBy
    )
  }
}
