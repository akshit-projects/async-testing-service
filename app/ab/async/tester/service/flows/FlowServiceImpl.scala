package ab.async.tester.service.flows

import ab.async.tester.constants.Constants
import ab.async.tester.domain.clients.kafka.KafkaConfig
import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.enums.StepStatus.IN_PROGRESS
import ab.async.tester.domain.execution.{Execution, ExecutionStep}
import ab.async.tester.domain.flow.{FlowVersion, Floww}
import ab.async.tester.domain.requests.RunFlowRequest
import ab.async.tester.domain.step.VariableSubstitution
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.cache.KafkaResourceCache
import ab.async.tester.library.clients.events.KafkaClient
import ab.async.tester.library.repository.execution.ExecutionRepository
import ab.async.tester.library.repository.flow.{FlowRepository, FlowVersionRepository}
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.Singleton
import com.typesafe.config.Config
import io.circe.syntax.EncoderOps
import org.apache.kafka.clients.producer.ProducerRecord
import play.api.{Configuration, Logger}

import java.time.Instant
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FlowServiceImpl @Inject()(
                                 flowRepository: FlowRepository,
                                 flowVersionRepository: FlowVersionRepository,
                                 configuration: Configuration,
                                 kafkaResourceCache: KafkaResourceCache,
                                 kafkaClient: KafkaClient,
                                 executionRepository: ExecutionRepository
                               )(implicit ec: ExecutionContext) extends FlowService {

  private implicit val logger: Logger = Logger(this.getClass)
  private val kafkaConfig = {
    val conf = configuration.get[Config]("kafka")
    KafkaConfig(
      bootstrapServers = conf.getString("bootstrapServers"),
    )
  }
  private val workerQueueTopic = configuration.get[String]("events.workerQueueTopic")
  private val serviceName = "FlowService"

  override def validateSteps(flow: Floww): Unit =
    MetricUtils.withServiceMetrics(serviceName, "validateSteps") {
      if (flow.steps.isEmpty) throw ValidationException("Flow must have at least one step")

      val stepNames = flow.steps.map(_.name)
      if (stepNames.distinct.length != stepNames.length) {
        val duplicates = stepNames.groupBy(identity).collect { case (n, dups) if dups.size > 1 => n }
        throw ValidationException(s"Duplicate step names found: ${duplicates.mkString(", ")}")
      }

      // Validate variable references
      val variableErrors = VariableSubstitution.validateVariableReferences(flow.steps)
      if (variableErrors.nonEmpty) {
        throw ValidationException(s"Variable reference validation failed: ${variableErrors.mkString("; ")}")
      }
    }

  override def addFlow(flow: Floww): Future[Floww] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "addFlow") {
      validateSteps(flow)
      val now = System.currentTimeMillis() / 1000
      val newFlow = flow.copy(createdAt = now, modifiedAt = now)
      for {
        createdFlow <- flowRepository.insert(newFlow)
        // Create initial version record
        flowVersion = FlowVersion(
          flowId = createdFlow.id.get,
          version = 1,
          steps = createdFlow.steps,
          createdAt = now,
          createdBy = flow.creator,
          description = flow.description
        )
        _ <- flowVersionRepository.insert(flowVersion)
      } yield {
        createdFlow
      }
    }

  override def getFlows(search: Option[String], flowIds: Option[List[String]], limit: Int, page: Int): Future[List[Floww]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getFlows") {
      flowRepository.findAll(search, flowIds, limit, page).recover {
        case e: Exception =>
          logger.error(s"Error retrieving flows: ${e.getMessage}", e)
          Nil
      }
    }

  override def getFlow(id: String): Future[Option[Floww]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getFlow") {
      flowRepository.findById(id).recover {
        case e: Exception =>
          logger.error(s"Error retrieving flow $id: ${e.getMessage}", e)
          None
      }
    }

  override def getFlowVersions(flowId: String): Future[List[FlowVersion]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getFlowVersions") {
      flowVersionRepository.findByFlowId(flowId)
    }

  override def getFlowVersion(flowId: String, version: Int): Future[Option[FlowVersion]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getFlowVersion") {
      flowVersionRepository.findByFlowIdAndVersion(flowId, version)
    }

  override def updateFlow(flow: Floww): Future[Boolean] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "updateFlow") {
      validateSteps(flow)
      for {
        existingFlowOpt <- flowRepository.findById(flow.id.getOrElse(""))
        result <- existingFlowOpt match {
          case Some(existingFlow) =>
            val nextVersion = existingFlow.version + 1
            val now = System.currentTimeMillis() / 1000
            val updatedFlow = flow.copy(modifiedAt = now, version = nextVersion)
            val flowVersion = FlowVersion(
              flowId = flow.id.get,
              version = nextVersion,
              steps = flow.steps,
              createdAt = now,
              createdBy = flow.creator,
              description = flow.description
            )

            for {
              // Create version record for the new version

              _ <- flowVersionRepository.insert(flowVersion)
              // Update the main flow record
              updateResult <- flowRepository.update(updatedFlow)
            } yield {
              if (updateResult) {
                logger.info(s"Flow updated: ${flow.id.getOrElse("")} to version $nextVersion")
              } else {
                logger.warn(s"Flow update failed: ${flow.id.getOrElse("")}")
              }
              updateResult
            }
          case None =>
            logger.warn(s"Flow not found for update: ${flow.id.getOrElse("")}")
            Future.successful(false)
        }
      } yield result
    }


  override def createExecution(runRequest: RunFlowRequest): Future[Execution] = {
    val executionId = java.util.UUID.randomUUID().toString

    for {
      flow <- getFlow(runRequest.flowId).map(_.get) // TODO handle not found
      execution <- {
        val execution = createExecutionEntity(executionId, flow, runRequest)
        executionRepository.saveExecution(execution).map(_ => execution)
      }
    } yield {
      // Publish to Kafka for workers to pick up
      val kafkaPublisher = kafkaResourceCache.getOrCreateProducer(Constants.SystemKafkaResourceId, kafkaClient.getKafkaPublisher(kafkaConfig))
      val message = execution.asJson.noSpaces
      val record = new ProducerRecord[String, String](workerQueueTopic, execution.id, message)

      // Send message asynchronously and log the result
      val sendFuture = Future { kafkaPublisher.send(record).get() }
      sendFuture.onComplete {
        case scala.util.Success(metadata) =>
          println(s"✅ Message sent successfully to topic: ${metadata.topic()}, partition: ${metadata.partition()}, offset: ${metadata.offset()}")
          println(s"📝 Message content: $message")
        case scala.util.Failure(exception) =>
          println(s"❌ Failed to send message to Kafka: ${exception.getMessage}")
          exception.printStackTrace()
      }

      // Return the execution instance directly
      execution
    }
  }



  private def createExecutionEntity(executionId: String, flow: Floww, runFlowRequest: RunFlowRequest) = {
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
        status = IN_PROGRESS,
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
      parameters = Option(runFlowRequest.params),
      testSuiteExecutionId = runFlowRequest.testSuiteExecutionId
    )
  }
}