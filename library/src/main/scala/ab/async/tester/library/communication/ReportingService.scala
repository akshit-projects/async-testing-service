package ab.async.tester.library.communication

import ab.async.tester.domain.alert.{ReportingConfig, SlackReportingCallbackConfig}
import ab.async.tester.domain.enums.ReportingCallbackType.SLACK
import ab.async.tester.domain.execution.Execution
import ab.async.tester.domain.flow.Floww
import ab.async.tester.library.clients.slack.SlackClient
import ab.async.tester.library.repository.execution.ExecutionRepository
import ab.async.tester.library.repository.flow.FlowRepository
import com.google.inject.ImplementedBy

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ReportingServiceImpl])
trait ReportingService {
  def exportToSlack(
      executionId: String,
      reportingConfig: ReportingConfig
  ): Future[Unit]
}

@Singleton
class ReportingServiceImpl @Inject() (
    executionRepository: ExecutionRepository,
    flowRepository: FlowRepository,
    slackClient: SlackClient
)(implicit ec: ExecutionContext)
    extends ReportingService {

  override def exportToSlack(
                     executionId: String,
                     reportingConfig: ReportingConfig
                   ): Future[Unit] = {
    executionRepository.findById(executionId).flatMap {
      case Some(execution) =>
        flowRepository.findById(execution.flowId).flatMap {
          case Some(flow) =>
            sendReport(flow, reportingConfig, execution)
          case None =>
            Future.failed(new Exception(s"Flow not found: ${execution.flowId}"))
        }
      case None =>
        Future.failed(new Exception(s"Execution not found: $executionId"))
    }
  }

  private def sendReport(flow: Floww, reportingConfig: ReportingConfig, execution: Execution) = {
    reportingConfig.callbackType match {
      case SLACK =>
        val slackConfig = reportingConfig.callbackConfig.asInstanceOf[SlackReportingCallbackConfig]
        // If webhookUrl is provided, use it. Otherwise, we might need a default or look it up from channel mapping.
        // For now, let's assume a configured webhook URL or the one from the execution's reportingConfig.

        slackClient.sendExecutionReport(
          slackConfig.webhookUrl,
          execution,
          flow.name
        )
    }
  }
}
