package ab.async.tester.service.opsgenie

import ab.async.tester.domain.alert.AlertFlowMapping
import ab.async.tester.domain.alert.triggermetas.OpsgenieAlertTriggerMeta
import ab.async.tester.domain.callbacks.OpsgeniePayload
import ab.async.tester.domain.enums.AlertProvider.OPSGENIE
import ab.async.tester.domain.requests.RunFlowRequest
import ab.async.tester.library.repository.alerts.flowmapping.AlertFlowMappingRepository
import ab.async.tester.service.flows.FlowService
import play.api.Logger

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OpsgenieServiceImpl @Inject() (
                                      mappingRepository: AlertFlowMappingRepository,
                                      flowService: FlowService
)(implicit ec: ExecutionContext)
    extends OpsgenieService {

  private val logger = Logger(this.getClass)

  override def handleWebhook(payload: OpsgeniePayload): Future[Unit] = {
    payload.action match {
      case "Create" =>
        val alert = payload.alert
        // Find all active mappings for the tags in the alert
        // We optimize by finding all mappings for each tag and then filtering by message pattern
        mappingRepository
          .findByAlertProvider(OPSGENIE).map { allMappings =>
            val matchingMappings = allMappings.filter { mapping =>
              mapping.triggerMeta match {
                case opsgenie: OpsgenieAlertTriggerMeta =>
                  opsgenie.alertId == payload.alert.alertId
                case _          => false
              }
            }

            if (matchingMappings.isEmpty) {
              logger.info(
                s"No matching flow mapping found for Opsgenie alert: ${alert.alertId}"
              )
              Future.successful(())
            } else {
              logger.info(
                s"Found ${matchingMappings.size} matching flow mappings for Opsgenie alert: ${alert.alertId}"
              )
              Future
                .sequence(matchingMappings.map(triggerFlow(_, alert.message)))
                .map(_ => ())
            }
          }
      case _ =>
        logger.debug(s"Ignoring Opsgenie webhook action: ${payload.action}")
        Future.successful(())
    }
  }

  private def triggerFlow(
      mapping: AlertFlowMapping,
      alertMessage: String
  ): Future[Unit] = {
    flowService.getFlow(mapping.flowId).flatMap {
      case Some(flow) =>
        val runRequest = RunFlowRequest(
          flowId = mapping.flowId,
          params =
            Map("triggerSource" -> "Opsgenie", "alertMessage" -> alertMessage),
          reportingConfig = mapping.reportingConfig,
          variables = List.empty
        )
        flowService.createExecution(runRequest).map { execution =>
          logger.info(
            s"Triggered flow execution ${execution.id} from Opsgenie alert mapping ${mapping.id.getOrElse("")}"
          )
        }
      case None =>
        logger.error(s"Flow not found for mapping: ${mapping.flowId}")
        Future.successful(())
    }
  }
}
