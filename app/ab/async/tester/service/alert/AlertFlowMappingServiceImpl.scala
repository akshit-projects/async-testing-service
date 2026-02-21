package ab.async.tester.service.alert

import ab.async.tester.domain.alert.AlertFlowMapping
import ab.async.tester.library.repository.alerts.flowmapping.AlertFlowMappingRepository
import com.google.inject.Inject

import scala.concurrent.Future


class AlertFlowMappingServiceImpl @Inject()(
    mappingRepository: AlertFlowMappingRepository
                         ) extends AlertFlowMappingService {

  override def createAlertFlowMapping(alertFlowMapping: AlertFlowMapping): Future[AlertFlowMapping] = {
    mappingRepository.insert(alertFlowMapping)
  }

  override def deleteAlertFlowMapping(alertFlowMappingId: String): Future[Boolean] = {
    mappingRepository.delete(alertFlowMappingId)
  }

  override def updateAlertFlowMapping(alertFlowMapping: AlertFlowMapping): Future[AlertFlowMapping] = {
    ???
  }
}
