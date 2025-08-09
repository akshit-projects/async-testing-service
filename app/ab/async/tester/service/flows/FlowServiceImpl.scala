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
                                 flowVersionRepository: FlowVersionRepository,
                                 flowRunner: FlowRunner
                               )(implicit ec: ExecutionContext) extends FlowServiceTrait {

  private implicit val logger: Logger = Logger(this.getClass)
  private val serviceName = "FlowService"

  override def validateSteps(steps: List[FlowStep]): Unit =
    MetricUtils.withServiceMetrics(serviceName, "validateSteps") {
      if (steps.isEmpty) throw ValidationException("Flow must have at least one step")

      val stepNames = steps.map(_.name)
      if (stepNames.distinct.length != stepNames.length) {
        val duplicates = stepNames.groupBy(identity).collect { case (n, dups) if dups.size > 1 => n }
        throw ValidationException(s"Duplicate step names found: ${duplicates.mkString(", ")}")
      }
    }

  override def addFlow(flow: Floww): Future[Floww] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "addFlow") {
      validateSteps(flow)
      val existingFlowFuture = flowRepository.findByName(flow.name)
      existingFlowFuture.map {
        case None =>
          val now = System.currentTimeMillis() / 1000
          val newFlow = flow.copy(createdAt = now, modifiedAt = now)
          flowRepository.insert(newFlow).map { created =>
            logger.info(s"Created new flow: ${created.id.getOrElse("")} - ${created.name}")
            created
          }
        case Some(existing) =>
          Future.failed(ValidationException(s"Flow with name '${flow.name}' already exists (id=${existing.id.getOrElse("")})"))
      }.flatten
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

  override def updateFlow(flow: Floww): Future[Boolean] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "updateFlow") {
      validateSteps(flow)
      val updated = flow.copy(modifiedAt = System.currentTimeMillis() / 1000)
      flowRepository.update(updated).map {
        case true =>
          logger.info(s"Flow updated: ${flow.id.getOrElse("")}")
          true
        case false =>
          logger.warn(s"Flow update failed: ${flow.id.getOrElse("")}")
          false
      }
    }
}