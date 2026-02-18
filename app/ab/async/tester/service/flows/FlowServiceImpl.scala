package ab.async.tester.service.flows

import ab.async.tester.constants.Constants
import ab.async.tester.domain.clients.kafka.KafkaConfig
import ab.async.tester.domain.common.{PaginatedResponse, PaginationMetadata}
import ab.async.tester.domain.execution.Execution
import ab.async.tester.domain.flow.{FlowVersion, Floww}
import ab.async.tester.domain.requests.RunFlowRequest
import ab.async.tester.domain.step._
import ab.async.tester.domain.variable.{VariableValidator, VariableValue}
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.cache.{KafkaResourceCache, RedisClient}
import ab.async.tester.library.clients.events.KafkaClient
import ab.async.tester.library.repository.execution.ExecutionRepository
import ab.async.tester.library.repository.flow.{FlowRepository, FlowVersionRepository}
import ab.async.tester.library.repository.resource.ResourceRepository
import ab.async.tester.library.utils.stepmeta.StepMetaExtensions.StepMetaOps
import ab.async.tester.library.utils.{MetricUtils, VariableSubstitution}
import com.google.inject.Singleton
import com.typesafe.config.Config
import io.circe.syntax.EncoderOps
import org.apache.kafka.clients.producer.ProducerRecord
import play.api.{Configuration, Logger}

import java.security.MessageDigest
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FlowServiceImpl @Inject() (
    flowRepository: FlowRepository,
    flowVersionRepository: FlowVersionRepository,
    configuration: Configuration,
    kafkaResourceCache: KafkaResourceCache,
    kafkaClient: KafkaClient,
    executionRepository: ExecutionRepository,
    resourceRepository: ResourceRepository,
    redisClient: RedisClient
)(implicit ec: ExecutionContext)
    extends FlowService {

  private implicit val logger: Logger = Logger(this.getClass)
  private val FLOW_IMPORTS_HASH_KEY = "fih"
  private val redisConnection = initiateRedisConnection()

  private def initiateRedisConnection() = {
    redisClient.getPool.getResource
  }

  private val kafkaConfig = {
    val conf = configuration.get[Config]("kafka")
    KafkaConfig(
      bootstrapServers = conf.getString("bootstrapServers")
    )
  }
  private val workerQueueTopic =
    configuration.get[String]("events.workerQueueTopic")
  private val serviceName = "FlowService"

  override def validateFlow(flow: Floww): Unit =
    MetricUtils.withServiceMetrics(serviceName, "validateSteps") {
      if (flow.steps.isEmpty)
        throw ValidationException("Flow must have at least one step")

      val stepNames = flow.steps.map(_.name)
      if (stepNames.distinct.length != stepNames.length) {
        val duplicates = stepNames.groupBy(identity).collect {
          case (n, dups) if dups.size > 1 => n
        }
        throw ValidationException(
          s"Duplicate step names found: ${duplicates.mkString(", ")}"
        )
      }

      // Validate variable references
      val variableErrors =
        VariableSubstitution.validateVariableReferences(flow.steps)
      if (variableErrors.nonEmpty) {
        throw ValidationException(
          s"Variable reference validation failed: ${variableErrors.mkString("; ")}"
        )
      }
      validateFlowVariables(flow)
    }

  /** Validates that all resource IDs referenced in flow steps exist in the
    * database
    */
  private def validateResourceIds(steps: List[FlowStep]): Future[Unit] = {
    val resourceIds = steps.flatMap(extractResourceId).distinct

    if (resourceIds.isEmpty) {
      Future.successful(())
    } else {
      Future
        .traverse(resourceIds) { resourceId =>
          resourceRepository.findById(resourceId).map {
            case Some(_) => ()
            case None    =>
              throw ValidationException(s"Resource not found: $resourceId")
          }
        }
        .map(_ => ())
    }
  }

  /** Extracts resource ID from a flow step meta
    */
  private def extractResourceId(step: FlowStep): Option[String] = {
    step.meta.getResourceId
  }

  /** Checks if a flow contains any of the specified step types
    */
  private def hasStepTypes(flow: Floww, stepTypes: List[String]): Boolean = {
    val flowStepTypes = flow.steps.map(_.stepType.stringified).toSet
    stepTypes.exists(stepType => flowStepTypes.contains(stepType.toLowerCase))
  }

  /** Validates flow variable definitions
    */
  private def validateFlowVariables(flow: Floww): Unit = {
    // Check for duplicate variable names
    val variableNames = flow.variables.map(_.name)
    if (variableNames.distinct.length != variableNames.length) {
      val duplicates = variableNames.groupBy(identity).collect {
        case (n, dups) if dups.size > 1 => n
      }
      throw ValidationException(
        s"Duplicate variable names found: ${duplicates.mkString(", ")}"
      )
    }

    // Validate variable names (alphanumeric + underscore, starting with letter)
    val invalidNames =
      flow.variables.filter(v => !v.name.matches("^[a-zA-Z][a-zA-Z0-9_]*$"))
    if (invalidNames.nonEmpty) {
      throw ValidationException(
        s"Invalid variable names: ${invalidNames.map(_.name).mkString(", ")}. Variable names must start with a letter and contain only alphanumeric characters and underscores."
      )
    }

    flow.variables.foreach { variable =>
      variable.defaultValue.foreach { defaultValue =>
        val validationResult =
          VariableValidator.validateValue(defaultValue, variable.`type`)
        if (!validationResult.isValid) {
          logger.error(
            s"Invalid default value for variable '${variable.name}': ${validationResult.errors.mkString(", ")}"
          )
          throw ValidationException(
            s"Invalid default value for variable '${variable.name}': ${validationResult.errors.mkString(", ")}"
          )
        }
      }
    }
  }

  /** Validates that all runtime variable references in steps have corresponding
    * variable definitions
    */
  private def validateRuntimeVariableReferences(
      flow: Floww,
      variables: List[VariableValue]
  ): Unit = {
    // variables with no default value must be present in the request
    val requiredVariables = flow.variables.filter(_.defaultValue.isEmpty).map(_.name).toSet
    val runTimeVariables = variables.filter(v => v.value.nonEmpty).map(_.name).toSet
    if (runTimeVariables.size < requiredVariables.size || (requiredVariables -- runTimeVariables).isEmpty) {
      val msg = s"Not all required runtime variables are passed. Missing variables ${requiredVariables -- runTimeVariables}."
      logger.error(msg)
      throw ValidationException(msg)
    }

    // first check that number of flow variables and passed variables are of same size
    val referencedVariables =
      FlowServiceAdapter.extractVariableReferencesFromSteps(flow.steps)
    val undefinedVariables = referencedVariables -- runTimeVariables

    if (undefinedVariables.nonEmpty) {
      logger.error(
        s"Undefined runtime variable references: ${undefinedVariables.mkString(", ")}. All variables referenced as $referencedVariables must be defined in the flow variables."
      )
      throw ValidationException(
        s"Undefined runtime variable references: ${undefinedVariables.mkString(", ")}. All variables referenced as $referencedVariables must be defined in the flow variables."
      )
    }
  }

  override def addFlow(flow: Floww): Future[Floww] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "addFlow") {
      validateFlow(flow)
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

  override def getFlows(
      search: Option[String],
      flowIds: Option[List[String]],
      orgId: Option[String],
      teamId: Option[String],
      stepTypes: Option[List[String]],
      limit: Int,
      page: Int
  ): Future[PaginatedResponse[Floww]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getFlows") {
      flowRepository
        .findAll(search, flowIds, orgId, teamId, limit, page)
        .map { case (flows, total) =>
          // Apply step type filtering if specified
          val filteredFlows = stepTypes match {
            // add this filter in DB query, this is paginated so response won't come
            case Some(types) if types.nonEmpty =>
              flows.filter(flow => hasStepTypes(flow, types))
            case _ => flows
          }

          PaginatedResponse(
            data = filteredFlows,
            pagination = PaginationMetadata(page, limit, total)
          )
        }
        .recover { case e: Exception =>
          logger.error(
            s"Error retrieving flows with pagination: ${e.getMessage}",
            e
          )
          PaginatedResponse(Nil, PaginationMetadata(page, limit, 0))
        }
    }

  override def getFlow(id: String): Future[Option[Floww]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getFlow") {
      flowRepository.findById(id).recover { case e: Exception =>
        logger.error(s"Error retrieving flow $id: ${e.getMessage}", e)
        None
      }
    }

  override def getFlowVersions(
      flowId: String,
      limit: Int,
      page: Int
  ): Future[PaginatedResponse[FlowVersion]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getFlowVersions") {
      flowVersionRepository
        .findByFlowIdWithCount(flowId, limit, page)
        .map { case (versions, total) =>
          PaginatedResponse(
            data = versions,
            pagination = PaginationMetadata(page, limit, total)
          )
        }
        .recover { case e: Exception =>
          logger.error(
            s"Error retrieving flow versions for flow $flowId: ${e.getMessage}",
            e
          )
          PaginatedResponse(Nil, PaginationMetadata(page, limit, 0))
        }
    }

  override def getFlowVersion(
      flowId: String,
      version: Int
  ): Future[Option[FlowVersion]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getFlowVersion") {
      flowVersionRepository.findByFlowIdAndVersion(flowId, version)
    }

  override def updateFlow(flow: Floww): Future[Boolean] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "updateFlow") {
      validateFlow(flow)
      for {
        _ <- validateResourceIds(flow.steps)
        existingFlowOpt <- flowRepository.findById(flow.id.getOrElse(""))
        result <- existingFlowOpt match {
          case Some(existingFlow) =>
            val now = System.currentTimeMillis() / 1000

            // Check if only name/description changed (steps and variables are the same)
            val stepsChanged = existingFlow.steps != flow.steps
            val variablesChanged = existingFlow.variables != flow.variables

            if (stepsChanged || variablesChanged) {
              // Steps or variables changed, create new version
              val nextVersion = existingFlow.version + 1
              val updatedFlow =
                flow.copy(modifiedAt = now, version = nextVersion)
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
                  logger.info(
                    s"Flow updated: ${flow.id.getOrElse("")} to version $nextVersion (steps or variables changed)"
                  )
                } else {
                  logger.warn(s"Flow update failed: ${flow.id.getOrElse("")}")
                }
                updateResult
              }
            } else {
              // Only metadata changed, update without creating new version
              val updatedFlow =
                flow.copy(modifiedAt = now, version = existingFlow.version)
              flowRepository.update(updatedFlow).map { updateResult =>
                if (updateResult) {
                  logger.info(
                    s"Flow metadata updated: ${flow.id.getOrElse("")} (no version change)"
                  )
                } else {
                  logger.warn(
                    s"Flow metadata update failed: ${flow.id.getOrElse("")}"
                  )
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

  override def createExecution(
      runRequest: RunFlowRequest
  ): Future[Execution] = {
    val executionId = java.util.UUID.randomUUID().toString

    for {
      flow <- getFlow(runRequest.flowId).map(_.get) // TODO handle not found
      _ = validateExecutionFlow(flow, runRequest.variables)
      execution <- {
        val execution = FlowServiceAdapter.createExecutionEntity(
          executionId,
          flow,
          runRequest
        )
        executionRepository.saveExecution(execution).map(_ => execution)
      }
    } yield {
      // Publish to Kafka for workers to pick up
      val kafkaPublisher = kafkaResourceCache.getOrCreateProducer(
        Constants.SystemKafkaResourceId,
        kafkaClient.getKafkaPublisher(kafkaConfig)
      )
      val message = execution.asJson.noSpaces
      val record = new ProducerRecord[String, String](
        workerQueueTopic,
        execution.id,
        message
      )

      // Send message asynchronously and log the result
      val sendFuture = Future { kafkaPublisher.send(record).get() }
      sendFuture.onComplete {
        case scala.util.Success(metadata) =>
          println(
            s"✅ Message sent successfully to topic: ${metadata.topic()}, partition: ${metadata.partition()}, offset: ${metadata.offset()}"
          )
          println(s"📝 Message content: $message")
        case scala.util.Failure(exception) =>
          println(s"❌ Failed to send message to Kafka: ${exception.getMessage}")
          exception.printStackTrace()
      }

      // Return the execution instance directly
      execution
    }
  }

  override def exportFlows(
      flowIds: Option[List[String]],
      orgId: Option[String],
      teamId: Option[String]
  ): Future[List[Floww]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "exportFlows") {
      // Reuse findAll but with high limit to export all matching flows
      // Only filter by provided params
      flowRepository
        .findAll(None, flowIds, orgId, teamId, 10000, 0)
        .map(_._1)
    }

  override def importFlows(
      flows: List[Floww],
      creatorId: String
  ): Future[List[Floww]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "importFlows") {
      flows.foreach(validateFlow)
      val creationTimestamp = System.currentTimeMillis() / 1000
      val flowsToInsert = getFlowsToInsert(flows, creatorId, creationTimestamp)

      val idempotencyKey = getIdempotencyKey(flowsToInsert)
      importFlowsIdempotencyCheck(idempotencyKey)

      flowRepository.insertAll(flowsToInsert).flatMap { insertedFlows =>
        // Create version 1 for all inserted flows
        val initialVersions = insertedFlows.map { flow =>
          FlowVersion(
            flowId = flow.id.get,
            version = 1,
            steps = flow.steps,
            createdAt = creationTimestamp,
            createdBy = creatorId,
            description = flow.description
          )
        }

        // We need to insert versions. FlowVersionRepository doesn't have batch insert yet.
        // We can do it in parallel since flows are already committed.
        // Ideally versions should be part of the same transaction but Repositories are separate.
        // Since we are not modifying Repositories heavily, we'll do sequence.
        Future
          .sequence(initialVersions.map(flowVersionRepository.insert))
          .map(_ => insertedFlows)
      }
    }

  private def getFlowsToInsert(flows: List[Floww], creatorId: String, creationTimestamp: Long) = {
    flows.map { flow =>
      flow.copy(
        id = None, // Strip ID to create new flow
        version = 1,
        creator = creatorId,
        createdAt = creationTimestamp,
        modifiedAt = creationTimestamp,
        // Retain org/team from payload if we want, or override?
        // Plan says: "retain the orgId and teamId from the JSON if present"
        orgId = flow.orgId,
        teamId = flow.teamId
      )
    }
  }

  private def importFlowsIdempotencyCheck(idempotencyKey: String): Unit = {
    if (redisConnection.exists(idempotencyKey)) {
      throw ValidationException("Duplicate import request detected")
    }
    // TTL of 1 minute for submitting bulk import request again for same request
    redisConnection.setex(idempotencyKey, 60, "1")
  }

  private def getIdempotencyKey(flows: List[Floww]): String = {
    val md5Hash = MessageDigest
      .getInstance("MD5")
      .digest(flows.asJson.noSpaces.getBytes)
      .map("%02x".format(_))
      .mkString
    s"$FLOW_IMPORTS_HASH_KEY:$md5Hash"
  }

  private def validateExecutionFlow(
      flow: Floww,
      variables: List[VariableValue]
  ): Unit = {
    validateFlow(flow)
    validateRuntimeVariableReferences(flow, variables)
  }
}
