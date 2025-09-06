package ab.async.tester.service.flows

import ab.async.tester.constants.Constants
import ab.async.tester.domain.clients.kafka.KafkaConfig
import ab.async.tester.domain.common.{PaginatedResponse, PaginationMetadata}
import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.enums.StepStatus.IN_PROGRESS
import ab.async.tester.domain.execution.{Execution, ExecutionStep}
import ab.async.tester.domain.flow.{FlowVersion, Floww}
import ab.async.tester.domain.requests.RunFlowRequest
import ab.async.tester.domain.step._
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
                                 executionRepository: ExecutionRepository,
                                 resourceRepository: ab.async.tester.library.repository.resource.ResourceRepository
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

  /**
   * Validates that all resource IDs referenced in flow steps exist in the database
   */
  private def validateResourceIds(steps: List[FlowStep]): Future[Unit] = {
    val resourceIds = steps.flatMap(extractResourceId).distinct

    if (resourceIds.isEmpty) {
      Future.successful(())
    } else {
      Future.traverse(resourceIds) { resourceId =>
        resourceRepository.findById(resourceId).map {
          case Some(_) => ()
          case None => throw ValidationException(s"Resource not found: $resourceId")
        }
      }.map(_ => ())
    }
  }

  /**
   * Extracts resource ID from a flow step meta
   */
  private def extractResourceId(step: FlowStep): Option[String] = {
    step.meta match {
      case httpMeta: HttpStepMeta => Some(httpMeta.resourceId)
      case kafkaSubMeta: KafkaSubscribeMeta => Some(kafkaSubMeta.resourceId)
      case kafkaPubMeta: KafkaPublishMeta => Some(kafkaPubMeta.resourceId)
      case sqlMeta: SqlStepMeta => Some(sqlMeta.resourceId)
      case redisMeta: RedisStepMeta => Some(redisMeta.resourceId)
      case _: DelayStepMeta => None // Delay steps don't use resources
    }
  }

  /**
   * Checks if a flow contains any of the specified step types
   */
  private def hasStepTypes(flow: Floww, stepTypes: List[String]): Boolean = {
    val flowStepTypes = flow.steps.map(_.stepType.toString.toLowerCase).toSet
    stepTypes.exists(stepType => flowStepTypes.contains(stepType.toLowerCase))
  }

  override def addFlow(flow: Floww): Future[Floww] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "addFlow") {
      validateSteps(flow)
      for {
        _ <- validateResourceIds(flow.steps)
        now = System.currentTimeMillis() / 1000
        newFlow = flow.copy(createdAt = now, modifiedAt = now)
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

  override def getFlows(search: Option[String], flowIds: Option[List[String]], orgId: Option[String], teamId: Option[String], stepTypes: Option[List[String]], limit: Int, page: Int): Future[PaginatedResponse[Floww]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getFlows") {
      flowRepository.findAll(search, flowIds, orgId, teamId, limit, page).map { case (flows, total) =>
        // Apply step type filtering if specified
        val filteredFlows = stepTypes match {
          case Some(types) if types.nonEmpty =>
            flows.filter(flow => hasStepTypes(flow, types))
          case _ => flows
        }

        PaginatedResponse(
          data = filteredFlows,
          pagination = PaginationMetadata(page, limit, total)
        )
      }.recover {
        case e: Exception =>
          logger.error(s"Error retrieving flows with pagination: ${e.getMessage}", e)
          PaginatedResponse(Nil, PaginationMetadata(page, limit, 0))
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

  override def getFlowVersions(flowId: String, limit: Int, page: Int): Future[PaginatedResponse[FlowVersion]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getFlowVersions") {
      flowVersionRepository.findByFlowIdWithCount(flowId, limit, page).map { case (versions, total) =>
        PaginatedResponse(
          data = versions,
          pagination = PaginationMetadata(page, limit, total)
        )
      }.recover {
        case e: Exception =>
          logger.error(s"Error retrieving flow versions for flow $flowId: ${e.getMessage}", e)
          PaginatedResponse(Nil, PaginationMetadata(page, limit, 0))
      }
    }

  override def getFlowVersion(flowId: String, version: Int): Future[Option[FlowVersion]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getFlowVersion") {
      flowVersionRepository.findByFlowIdAndVersion(flowId, version)
    }

  override def updateFlow(flow: Floww): Future[Boolean] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "updateFlow") {
      validateSteps(flow)
      for {
        _ <- validateResourceIds(flow.steps)
        existingFlowOpt <- flowRepository.findById(flow.id.getOrElse(""))
        result <- existingFlowOpt match {
          case Some(existingFlow) =>
            val now = System.currentTimeMillis() / 1000

            // Check if only name/description changed (steps are the same)
            val stepsChanged = existingFlow.steps != flow.steps

            if (stepsChanged) {
              // Steps changed, create new version
              val nextVersion = existingFlow.version + 1
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
                  logger.info(s"Flow updated: ${flow.id.getOrElse("")} to version $nextVersion (steps changed)")
                } else {
                  logger.warn(s"Flow update failed: ${flow.id.getOrElse("")}")
                }
                updateResult
              }
            } else {
              // Only metadata changed, update without creating new version
              val updatedFlow = flow.copy(modifiedAt = now, version = existingFlow.version)
              flowRepository.update(updatedFlow).map { updateResult =>
                if (updateResult) {
                  logger.info(s"Flow metadata updated: ${flow.id.getOrElse("")} (no version change)")
                } else {
                  logger.warn(s"Flow metadata update failed: ${flow.id.getOrElse("")}")
                }
                updateResult
              }
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