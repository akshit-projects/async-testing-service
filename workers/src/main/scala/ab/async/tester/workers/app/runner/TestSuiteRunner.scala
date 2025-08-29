package ab.async.tester.workers.app.runner

import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.requests.RunFlowRequest
import ab.async.tester.domain.testsuite.{TestSuiteExecution, TestSuiteFlowExecution}
import ab.async.tester.library.repository.flow.{FlowRepository, FlowVersionRepository}
import ab.async.tester.library.repository.testsuite.TestSuiteExecutionRepository
import ab.async.tester.workers.app.clients.kafka.KafkaConsumer
import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import com.google.inject.{ImplementedBy, Inject, Singleton}
import io.circe.generic.auto._
import io.circe.jawn.decode
import io.circe.syntax._
import play.api.{Configuration, Logger}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Test suite runner trait for worker processes
 */
@ImplementedBy(classOf[TestSuiteRunnerImpl])
trait TestSuiteRunner {
  /**
   * Start consuming test suite execution messages from Kafka
   */
  def startTestSuiteConsumer(): Unit

  /**
   * Execute a test suite
   * @param testSuiteExecution the test suite execution to run
   * @return Future indicating completion
   */
  def executeTestSuite(testSuiteExecution: TestSuiteExecution): Future[Unit]
}

/**
 * Worker implementation that consumes test suite execution messages and executes test suites
 */
@Singleton
class TestSuiteRunnerImpl @Inject()(
  testSuiteExecutionRepository: TestSuiteExecutionRepository,
  flowRepository: FlowRepository,
  flowVersionRepository: FlowVersionRepository,
  configuration: Configuration
)(implicit system: ActorSystem, ec: ExecutionContext) extends TestSuiteRunner {

  private implicit val logger: Logger = Logger(this.getClass)
  private val runnerName = "TestSuiteRunner"

  // Kafka configuration
  private val testSuiteExecutionTopic = configuration.get[String]("events.testSuiteExecutionTopic")
  private val workerQueueTopic = configuration.get[String]("events.workerQueueTopic")

  override def startTestSuiteConsumer(): Unit = {
    logger.info(s"$runnerName starting to consume from topic: $testSuiteExecutionTopic")

    KafkaConsumer.subscribeTopic(testSuiteExecutionTopic)
      .mapAsync(1) { msg =>
        val messageValue = msg.record.value()
        logger.debug(s"Received test suite execution message: $messageValue")

        // Parse the test suite execution from JSON
        decode[TestSuiteExecution](messageValue) match {
          case Right(testSuiteExecution) =>
            logger.info(s"Processing test suite execution: ${testSuiteExecution.id}")
            executeTestSuite(testSuiteExecution).map { _ =>
              // Commit the Kafka message after successful processing
              msg.committableOffset
            }.recover {
              case e: Exception =>
                logger.error(s"Failed to process test suite execution ${testSuiteExecution.id}: ${e.getMessage}", e)
                // Still commit to avoid reprocessing the same failed message
                msg.committableOffset
            }
          case Left(error) =>
            logger.error(s"Failed to parse test suite execution from Kafka message: $error")
            // Commit malformed messages to avoid infinite reprocessing
            Future.successful(msg.committableOffset)
        }
      }
      .mapAsync(10)(_.commitScaladsl())
      .runWith(Sink.ignore)

    logger.info(s"$runnerName consumer started successfully")
  }

  override def executeTestSuite(testSuiteExecution: TestSuiteExecution): Future[Unit] = {
    logger.info(s"Executing test suite: ${testSuiteExecution.testSuiteName} (${testSuiteExecution.id})")

    // Update status to IN_PROGRESS
    updateTestSuiteExecutionStatus(testSuiteExecution.id, ExecutionStatus.InProgress, 0, 0).flatMap { _ =>
      if (testSuiteExecution.runUnordered) {
        executeFlowsInParallel(testSuiteExecution)
      } else {
        executeFlowsSequentially(testSuiteExecution)
      }
    }.recover {
      case ex =>
        logger.error(s"Test suite execution failed: ${testSuiteExecution.id}", ex)
        updateTestSuiteExecutionStatus(testSuiteExecution.id, ExecutionStatus.Failed, 0, testSuiteExecution.totalFlows)
    }
  }

  private def executeFlowsInParallel(testSuiteExecution: TestSuiteExecution): Future[Unit] = {
    logger.info(s"Executing ${testSuiteExecution.totalFlows} flows in parallel for test suite: ${testSuiteExecution.id}")

    val flowExecutionFutures = testSuiteExecution.flowExecutions.map { flowExecution =>
      executeFlow(testSuiteExecution.id, flowExecution)
    }

    Future.sequence(flowExecutionFutures).map { results =>
      val completedFlows = results.count(_.isDefined)
      val failedFlows = results.count(_.isEmpty)
      val finalStatus = if (failedFlows > 0) ExecutionStatus.Failed else ExecutionStatus.Completed

      updateTestSuiteExecutionStatus(testSuiteExecution.id, finalStatus, completedFlows, failedFlows, isCompleted = true)
      logger.info(s"Test suite execution completed: ${testSuiteExecution.id} - $completedFlows completed, $failedFlows failed")
    }
  }

  private def executeFlowsSequentially(testSuiteExecution: TestSuiteExecution): Future[Unit] = {
    logger.info(s"Executing ${testSuiteExecution.totalFlows} flows sequentially for test suite: ${testSuiteExecution.id}")

    def executeNext(remaining: List[TestSuiteFlowExecution], completedCount: Int, failedCount: Int): Future[Unit] = {
      remaining match {
        case Nil =>
          val finalStatus = if (failedCount > 0) ExecutionStatus.Failed else ExecutionStatus.Completed
          updateTestSuiteExecutionStatus(testSuiteExecution.id, finalStatus, completedCount, failedCount, isCompleted = true)
          logger.info(s"Test suite execution completed: ${testSuiteExecution.id} - $completedCount completed, $failedCount failed")
          Future.successful(())

        case flowExecution :: tail =>
          executeFlow(testSuiteExecution.id, flowExecution).flatMap {
            case Some(_) =>
              // Flow succeeded, continue with next
              val newCompletedCount = completedCount + 1
              updateTestSuiteExecutionStatus(testSuiteExecution.id, ExecutionStatus.InProgress, newCompletedCount, failedCount)
              executeNext(tail, newCompletedCount, failedCount)
            case None =>
              // Flow failed, stop execution (for sequential execution, we might want to stop on first failure)
              val newFailedCount = failedCount + 1
              updateTestSuiteExecutionStatus(testSuiteExecution.id, ExecutionStatus.Failed, completedCount, newFailedCount, isCompleted = true)
              logger.warn(s"Test suite execution stopped due to flow failure: ${testSuiteExecution.id}")
              Future.successful(())
          }
      }
    }

    executeNext(testSuiteExecution.flowExecutions, 0, 0)
  }

  private def executeFlow(testSuiteExecutionId: String, flowExecution: TestSuiteFlowExecution): Future[Option[String]] = {
    logger.info(s"Executing flow ${flowExecution.flowId} for test suite execution: $testSuiteExecutionId")

    // Determine the flow version to use
    val flowVersionFuture = if (flowExecution.flowVersion > 0) {
      // Use specific version
      flowVersionRepository.findByFlowIdAndVersion(flowExecution.flowId, flowExecution.flowVersion)
        .map(_.map(_.version))
    } else {
      // Use latest version or flow's current version
      flowRepository.findById(flowExecution.flowId).map(_.map(_.version))
    }

    flowVersionFuture.flatMap {
      case Some(version) =>
        val runFlowRequest = RunFlowRequest(
          flowId = flowExecution.flowId,
          params = flowExecution.parameters.getOrElse(Map.empty)
        )

        // Create flow execution with test suite context
//        flowExecutionService.createExecutionWithTestSuite(runFlowRequest, Some(testSuiteExecutionId)).map { executionResponse =>
//          logger.info(s"Created flow execution ${executionResponse.id} for flow ${flowExecution.flowId} in test suite $testSuiteExecutionId")
//          Some(executionResponse.id)
//        }.recover {
//          case ex =>
//            logger.error(s"Failed to create flow execution for flow ${flowExecution.flowId} in test suite $testSuiteExecutionId", ex)
//            None
//        }
        Future.successful(None) // TODO fix this
      case None =>
        logger.error(s"Flow not found: ${flowExecution.flowId}")
        Future.successful(None)
    }
  }

  private def updateTestSuiteExecutionStatus(id: String, status: ExecutionStatus, completedFlows: Int, failedFlows: Int, isCompleted: Boolean = false): Future[Unit] = {
    testSuiteExecutionRepository.updateStatus(id, status, completedFlows, failedFlows, isCompleted).map { _ =>
      logger.debug(s"Updated test suite execution status: $id -> $status (completed: $completedFlows, failed: $failedFlows)")
    }.recover {
      case ex =>
        logger.error(s"Failed to update test suite execution status: $id", ex)
    }
  }
}
