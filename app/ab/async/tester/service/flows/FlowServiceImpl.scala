package ab.async.tester.service.flows

import ab.async.tester.constants.StepFunctions
import ab.async.tester.domain.flow.{Floww, FlowVersion}
import ab.async.tester.domain.step.FlowStep
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.repository.flow.{FlowRepository, FlowVersionRepository}
import ab.async.tester.library.utils.MetricUtils
import akka.NotUsed
import akka.stream.scaladsl.Source
import com.google.inject.Singleton
import play.api.Logger

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class FlowServiceImpl @Inject()(
                                 flowRepository: FlowRepository,
                                 flowVersionRepository: FlowVersionRepository
                               )(implicit ec: ExecutionContext) extends FlowServiceTrait {

  private implicit val logger: Logger = Logger(this.getClass)
  private val serviceName = "FlowService"

  override def validateSteps(flow: Floww): Unit =
    MetricUtils.withServiceMetrics(serviceName, "validateSteps") {
      if (flow.steps.isEmpty) throw ValidationException("Flow must have at least one step")

      val stepNames = flow.steps.map(_.name)
      if (stepNames.distinct.length != stepNames.length) {
        val duplicates = stepNames.groupBy(identity).collect { case (n, dups) if dups.size > 1 => n }
        throw ValidationException(s"Duplicate step names found: ${duplicates.mkString(", ")}")
      }
    }

  override def addFlow(flow: Floww): Future[Floww] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "addFlow") {
      validateSteps(flow)
      val existingFlowFuture = flowRepository.findByName(flow.name)
      existingFlowFuture.flatMap {
        case None =>
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
            logger.info(s"Created new flow: ${createdFlow.id.getOrElse("")} - ${createdFlow.name} with initial version")
            createdFlow
          }
        case Some(existing) =>
          Future.failed(ValidationException(s"Flow with name '${flow.name}' already exists (id=${existing.id.getOrElse("")})"))
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
}