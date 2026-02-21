package ab.async.tester.service.opsgenie

import ab.async.tester.domain.callbacks.OpsgeniePayload
import com.google.inject.ImplementedBy

import scala.concurrent.Future

@ImplementedBy(classOf[OpsgenieServiceImpl])
trait OpsgenieService {
  def handleWebhook(payload: OpsgeniePayload): Future[Unit]
}
