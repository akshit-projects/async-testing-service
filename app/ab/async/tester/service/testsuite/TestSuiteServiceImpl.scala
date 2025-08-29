package ab.async.tester.service.testsuite

import ab.async.tester.constants.Constants
import ab.async.tester.domain.clients.kafka.KafkaConfig
import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.requests.RunTestSuiteRequest
import ab.async.tester.domain.testsuite.{TestSuite, TestSuiteExecution, TestSuiteFlowExecution}
import ab.async.tester.library.cache.KafkaResourceCache
import ab.async.tester.library.clients.events.KafkaClient
import ab.async.tester.library.repository.flow.FlowRepository
import ab.async.tester.library.repository.flow.FlowVersionRepository
import ab.async.tester.library.repository.testsuite.{TestSuiteExecutionRepository, TestSuiteRepository}
import com.google.inject.{Inject, Singleton}
import com.typesafe.config.Config
import io.circe.generic.auto._
import io.circe.syntax._
import org.apache.kafka.clients.producer.ProducerRecord
import play.api.{Configuration, Logger}

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
                                      flowVersionRepository: FlowVersionRepository,
                                      kafkaResourceCache: KafkaResourceCache,
                                      kafkaClient: KafkaClient,
                                      configuration: Configuration
)(implicit ec: ExecutionContext) extends TestSuiteService {

  private val logger = Logger(this.getClass)
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
      testSuiteExecution <- createTestSuiteExecution(testSuite, request)
      _ <- testSuiteExecutionRepository.insert(testSuiteExecution)
      _ <- publishTestSuiteExecution(testSuiteExecution)
    } yield testSuiteExecution
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

  private def createTestSuiteExecution(testSuite: TestSuite, request: RunTestSuiteRequest): Future[TestSuiteExecution] = {
    val executionId = UUID.randomUUID().toString
    val now = Instant.now()
    
    // Create initial flow executions (empty execution IDs, will be filled when flows are actually executed)
    val flowExecutions = testSuite.flows.map { flowConfig =>
      TestSuiteFlowExecution(
        flowId = flowConfig.flowId,
        flowVersion = 0, // Will be determined when flow is executed
        executionId = "", // Will be set when flow execution is created
        status = ExecutionStatus.Todo,
        startedAt = now,
        parameters = mergeParameters(flowConfig.parameters, request.globalParameters)
      )
    }

    Future.successful(TestSuiteExecution(
      id = executionId,
      testSuiteId = testSuite.id.get,
      testSuiteName = testSuite.name,
      status = ExecutionStatus.Todo,
      startedAt = now,
      flowExecutions = flowExecutions,
      runUnordered = testSuite.runUnordered,
      triggeredBy = request.triggeredBy,
      totalFlows = testSuite.flows.length
    ))
  }

  private def mergeParameters(flowParams: Option[Map[String, String]], globalParams: Option[Map[String, String]]): Option[Map[String, String]] = {
    (flowParams, globalParams) match {
      case (Some(fp), Some(gp)) => Some(fp ++ gp) // Global parameters override flow parameters
      case (Some(fp), None) => Some(fp)
      case (None, Some(gp)) => Some(gp)
      case (None, None) => None
    }
  }

  private def publishTestSuiteExecution(testSuiteExecution: TestSuiteExecution): Future[Unit] = {
    val message = testSuiteExecution.asJson.noSpaces
    val kafkaPublisher = kafkaResourceCache.getOrCreateProducer(Constants.SystemKafkaResourceId, kafkaClient.getKafkaPublisher(kafkaConfig))
    val record = new ProducerRecord[String, String](testSuiteExecutionTopic, testSuiteExecution.id, message)

    val sendFuture = Future { kafkaPublisher.send(record).get() }
    sendFuture.map { metadata =>
      println(s"✅ Message sent successfully to topic: ${metadata.topic()}, partition: ${metadata.partition()}, offset: ${metadata.offset()}")
      println(s"📝 Message content: $message")
    }.recover {
      case exception: Exception =>
        println(s"❌ Failed to send message to Kafka: ${exception.getMessage}")
        exception.printStackTrace()
    }
  }
}
