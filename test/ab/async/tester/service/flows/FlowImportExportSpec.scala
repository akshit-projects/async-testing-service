package ab.async.tester.service.flows

import ab.async.tester.domain.enums.StepType
import ab.async.tester.domain.flow.{FlowVersion, Floww}
import ab.async.tester.domain.resource.ResourceConfig
import ab.async.tester.domain.step.FlowStep
import ab.async.tester.domain.step.metas.HttpStepMeta
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.cache.RedisClient
import ab.async.tester.library.clients.events.KafkaClient
import ab.async.tester.library.repository.execution.ExecutionRepository
import ab.async.tester.library.repository.flow.{FlowRepository, FlowVersionRepository}
import ab.async.tester.library.repository.resource.ResourceRepository
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import redis.clients.jedis.{Jedis, JedisPool}

import scala.concurrent.{ExecutionContext, Future}

class FlowImportExportSpec
    extends PlaySpec
    with MockitoSugar
    with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val mockFlowRepo = mock[FlowRepository]
  val mockVersionRepo = mock[FlowVersionRepository]
  val mockConfig = mock[play.api.Configuration]
  val mockKafkaCache = mock[ab.async.tester.library.cache.KafkaResourceCache]
  val mockKafkaClient = mock[KafkaClient]
  val mockExecutionRepo = mock[ExecutionRepository]
  val mockResourceRepo = mock[ResourceRepository]
  val mockRedisClient = mock[RedisClient]
  val mockJedisPool = mock[JedisPool]
  val mockJedis = mock[Jedis]

  // Mock config for kafka
  when(mockConfig.get[com.typesafe.config.Config](eqTo("kafka"))(any()))
    .thenReturn(
      com.typesafe.config.ConfigFactory
        .parseString("bootstrapServers=\"localhost:9092\"")
    )
  when(mockConfig.get[String](eqTo("events.workerQueueTopic"))(any()))
    .thenReturn("worker-topic")

  when(mockRedisClient.getPool).thenReturn(mockJedisPool)
  when(mockJedisPool.getResource).thenReturn(mockJedis)

  val service = new FlowServiceImpl(
    mockFlowRepo,
    mockVersionRepo,
    mockConfig,
    mockKafkaCache,
    mockKafkaClient,
    mockExecutionRepo,
    mockResourceRepo,
    mockRedisClient
  )

  "FlowServiceImpl" should {
    "export flows successfully" in {
      val flow = Floww(
        Some("1"),
        "flow1",
        None,
        "creator1",
        List(
          FlowStep(
            id = Some("step1"),
            name = "Step 1",
            stepType = StepType.HttpRequest,
            HttpStepMeta("res1", None, None),
            1000
          )
        )
      )
      when(
        mockFlowRepo.findAll(
          any(),
          eqTo(Some(List("1"))),
          any(),
          any(),
          any(),
          any()
        )
      )
        .thenReturn(Future.successful((List(flow), 1L)))

      val result = service.exportFlows(Some(List("1")), None, None).futureValue
      result mustBe List(flow)
    }

    "import flows successfully" in {
      val step = FlowStep(
        id = Some("step1"),
        name = "step1",
        stepType = StepType.HttpRequest,
        meta = HttpStepMeta("res1", None, None),
        timeoutMs = 1000
      )
      val flowToImport =
        Floww(Some("oldId"), "flow1", None, "oldCreator", List(step))
      val creatorId = "newCreator"

      // Mock resource validation
      when(mockResourceRepo.findById("res1")).thenReturn(
        Future.successful(Some(mock[ResourceConfig]))
      )

      // Mock Redis (Idempotency) - key does not exist
      when(mockJedis.exists(any[String])).thenReturn(false)
      when(mockJedis.setex(any[String], any[Int], any[String])).thenReturn("OK")

      // Mock insertAll
      when(mockFlowRepo.insertAll(any[List[Floww]])).thenAnswer { invocation =>
        val args = invocation.getArguments
        val flows = args(0).asInstanceOf[List[Floww]]
        // Verify flow logic
        flows.head.id must not be Some(
          "oldId"
        ) // ID should be stripped (impl generates new UUID or None?)
        // The implementation does: flow.copy(id = None ...) then insertAll does: f.copy(id = UUID...)
        // But insertAll mock receives the list *before* Repo assigns IDs if Repo does it.
        // Wait, check impl:
        // Impl: val flowsToInsert = flows.map(_.copy(id = None...))
        // Repo.insertAll: val flowsWithIds = flowsList.map(f => f.copy(id = UUID))
        // So Repo mock receives flows with id=None.
        // Let's return them with fixed IDs to simulate DB insertion.
        val inserted = flows.map(_.copy(id = Some("newUuid")))
        Future.successful(inserted)
      }

      // Mock version insert
      when(mockVersionRepo.insert(any[FlowVersion]))
        .thenReturn(Future.successful(mock[FlowVersion]))

      val result =
        service.importFlows(List(flowToImport), creatorId).futureValue

      result.head.id mustBe Some("newUuid")
      result.head.creator mustBe creatorId
      result.head.version mustBe 1
    }

    "fail import on duplicate request (Idempotency)" in {
      val flowToImport = Floww(Some("1"), "flow1", None, "c", List.empty)

      // key exists
      when(mockJedis.exists(any[String])).thenReturn(true)

      assertThrows[ValidationException] {
        service.importFlows(List(flowToImport), "user").futureValue
      }
    }
  }
}
