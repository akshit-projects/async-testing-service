package ab.async.tester.service.alert

import ab.async.tester.domain.alert.AlertFlowMapping
import com.google.inject.ImplementedBy

import scala.concurrent.Future

@ImplementedBy(classOf[AlertFlowMappingServiceImpl])
trait AlertFlowMappingService {

  def createAlertFlowMapping(alertFlowMapping: AlertFlowMapping): Future[AlertFlowMapping]

  def deleteAlertFlowMapping(alertFlowMappingId: String): Future[Boolean]

  def updateAlertFlowMapping(alertFlowMapping: AlertFlowMapping): Future[AlertFlowMapping]

}
