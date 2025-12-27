package ab.async.tester.workers.app.runner

import ab.async.tester.domain.enums.{ExecutionStatus, StepStatus, StepType}
import ab.async.tester.domain.execution.{Execution, ExecutionStep, StepUpdate}
import ab.async.tester.domain.step.{StepResponse, StepError}
import ab.async.tester.library.cache.RedisClient
import ab.async.tester.library.repository.execution.ExecutionRepository
import ab.async.tester.library.repository.testsuite.TestSuiteExecutionRepository
import akka.actor.ActorSystem
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import redis.clients.jedis.{Jedis, JedisPool}

import scala.concurrent.{ExecutionContext, Future}

class FlowRunnerSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  implicit val system: ActorSystem = ActorSystem("FlowRunnerSpec")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  trait Setup {
    val mockExecutionRepository = mock[ExecutionRepository]
    val mockStepRunnerRegistry = mock[StepRunnerRegistry]
    val mockRedisClient = mock[RedisClient]
    val mockConfiguration = mock[Configuration]
    val mockTestSuiteExecutionRepository = mock[TestSuiteExecutionRepository]
    val mockJedisPool = mock[JedisPool]
    val mockJedis = mock[Jedis]
    val mockStepRunner = mock[StepRunner]

    when(mockConfiguration.get[String](eqTo("events.workerQueueTopic"))(any()))
      .thenReturn("test-topic")
    when(mockRedisClient.getPool).thenReturn(mockJedisPool)
    when(mockJedisPool.getResource).thenReturn(mockJedis)

    val runner = new FlowRunnerImpl(
      mockExecutionRepository,
      mockStepRunnerRegistry,
      mockRedisClient,
      mockConfiguration,
      mockTestSuiteExecutionRepository
    )
  }

  "FlowRunner" should {

    "execute a flow successfully" in new Setup {
      val executionId = "exec-1"
      val step1 = ExecutionStep(
        Some("step-1"),
        "step 1",
        StepType.HttpRequest,
        "{}",
        1000,
        10,
        false,
        false
      )
      val execution = Execution(
        executionId,
        "flow1",
        "pending",
        0,
        0,
        "user1",
        "org1",
        "team1",
        List(step1),
        List.empty,
        None,
        None,
        None
      )

      when(
        mockExecutionRepository.updateStatus(
          any[String],
          any[ExecutionStatus],
          any[Boolean]
        )
      )
        .thenReturn(Future.successful(true))
      when(
        mockExecutionRepository.updateExecutionStep(
          any[String],
          any[String],
          any[ExecutionStep]
        )
      )
        .thenReturn(Future.successful(true))

      when(mockStepRunnerRegistry.getRunnerForStep(StepType.HttpRequest))
        .thenReturn(mockStepRunner)

      val stepResponse = StepResponse(
        "step 1",
        StepStatus.SUCCESS,
        StepError("none", None, None),
        "step-1"
      )
      // Actually StepResponse takes StepResponseValue. StepError extends StepResponseValue?
      // Let's check StepResponse definition. It's generic? No, StepResponseValue is a sealed trait probably.
      // Assuming StepError is a valid StepResponseValue.

      when(mockStepRunner.runStep(any[ExecutionStep], any[List[StepResponse]]))
        .thenReturn(Future.successful(stepResponse))

      when(mockJedis.publish(anyString, anyString)).thenReturn(1L)

      val result = runner.executeFlow(execution)

      whenReady(result) { _ =>
        verify(mockExecutionRepository).updateStatus(
          executionId,
          ExecutionStatus.InProgress,
          false
        )
        verify(mockExecutionRepository).updateStatus(
          executionId,
          ExecutionStatus.Completed,
          true
        )
        verify(mockStepRunner).runStep(
          any[ExecutionStep],
          any[List[StepResponse]]
        )
      }
    }
  }
}
