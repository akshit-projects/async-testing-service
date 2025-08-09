package ab.async.tester.service.flows

import ab.async.tester.constants.Constants
import ab.async.tester.domain.clients.kafka.KafkaConfig
import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.enums.StepStatus.TODO
import ab.async.tester.domain.execution.{Execution, ExecutionStep}
import ab.async.tester.domain.flow.Floww
import ab.async.tester.domain.requests.RunFlowRequest
import ab.async.tester.library.cache.KafkaResourceCache
import ab.async.tester.library.clients.events.KafkaClient
import ab.async.tester.library.clients.redis.RedisPubSubService
import ab.async.tester.library.repository.execution.ExecutionRepository
import akka.NotUsed
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import akka.stream.Materializer
import com.typesafe.config.Config
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.apache.kafka.clients.producer.ProducerRecord
import play.api.Configuration

import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

class FlowExecutionServiceImpl(
                                executionRepository: ExecutionRepository,
                                redisPubSubService: RedisPubSubService,
                                kafkaResourceCache: KafkaResourceCache,
                                configuration: Configuration,
                                kafkaClient: KafkaClient,
                                flowService: FlowServiceTrait
                              )(implicit ec: ExecutionContext, mat: Materializer) extends FlowExecutionService {

  private val kafkaConfig = {
    val conf = configuration.get[Config]("kafka")
    KafkaConfig(
      bootstrapServers = conf.getString("bootstrapServers"),
    )
  }
  private val workerQueueTopic = configuration.get[String]("events.workerQueueTopic")
  override def startExecution(runRequest: RunFlowRequest): Future[ExecutionStreams] = {
    val executionId = java.util.UUID.randomUUID().toString
    val now = Instant.now().toEpochMilli
    val (queue, source) = Source.queue[Json](64, OverflowStrategy.dropHead).preMaterialize()

    for {
      flow <- flowService.getFlow(runRequest.flowId).map(_.get) // TODO handle not found
      execution <- {
        val execution = createExecution(executionId, flow, runRequest)
        executionRepository.saveExecution(execution).map(_ => execution)
      }
    } yield {
      val kafkaPublisher = kafkaResourceCache.getOrCreateProducer(Constants.SystemKafkaResourceId, kafkaClient.getKafkaPublisher(kafkaConfig))
      kafkaPublisher.send(new ProducerRecord[String, String](workerQueueTopic, execution.asJson.noSpaces))
      redisPubSubService.registerQueue(executionId, queue)

      // Map Json to String for WebSocket
      val stringSource: Source[String, NotUsed] = source.map(_.noSpaces)

      ExecutionStreams(queue, stringSource, executionId)
    }
  }

  private def createExecution(executionId: String, flow: Floww, runFlowRequest: RunFlowRequest) = {
    val now = Instant.now()
    val execSteps = flow.steps.map { step =>
      ExecutionStep(
        id = step.id,
        name = step.name,
        stepType = step.stepType,
        meta = step.meta,
        timeoutMs = step.timeoutMs,
        runInBackground = step.runInBackground,
        continueOnSuccess = step.continueOnSuccess,
        status = TODO,
        startedAt = now,
        logs = List.empty,
        response = None
      )
    }
    Execution(
      id = executionId,
      flowId = flow.id.get,
      flowVersion = flow.version,
      status = ExecutionStatus.Todo,
      startedAt = now,
      steps = execSteps,
      updatedAt = now,
      parameters = Option(runFlowRequest.params)
    )
  }

  override def stopExecution(executionId: String): Unit = {
    redisPubSubService.unregisterQueue(executionId)
  }
}
