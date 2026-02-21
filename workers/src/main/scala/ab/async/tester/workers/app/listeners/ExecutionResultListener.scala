package ab.async.tester.workers.app.listeners

import ab.async.tester.domain.execution.Execution
import ab.async.tester.library.communication.ReportingService
import ab.async.tester.library.utils.DecodingUtils
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.CommittableMessage
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.google.inject.Inject
import io.circe.jawn.decode
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.{Configuration, Logger}

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ExecutionResultListener @Inject()(
    configuration: Configuration,
    reportingService: ReportingService
)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) {

  private implicit val logger = Logger(this.getClass)
  private val bootstrapServers =
    configuration.get[String]("kafka.bootstrapServers")
  private val topic = configuration.get[String]("events.executionResultTopic")
  private val groupId = "async-tester-reporting"

  def start(): Unit = {
    logger.info(s"Starting ExecutionResultListener on topic: $topic")

    val consumerSettings =
      ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
        .withBootstrapServers(bootstrapServers)
        .withGroupId(groupId)

    Consumer
      .committableSource(consumerSettings, Subscriptions.topics(topic))
      .mapAsync(1) { msg =>
        handleExecutionMsg(msg)
      }
      .mapAsync(1)(_.commitScaladsl())
      .runWith(Sink.ignore)
      .onComplete { _ =>
        logger.info("ExecutionResultListener stream stopped")
      }
  }

  private def handleExecutionMsg(msg: CommittableMessage[String, String]) = {
    val executionJson = msg.record.value()
    val execution = DecodingUtils.decodeWithErrorLogs[Execution](executionJson)
    execution.reportingConfig match {
      case Some(reportingConfig) =>
        logger.info(
          s"Received execution result for ${execution.id} with reporting config. Triggering report."
        )
        reportingService
          .exportToSlack(
            execution.id,
            reportingConfig
          )
          .recover { case ex =>
            logger.error(
              s"Failed to send Slack report for execution ${execution.id}",
              ex
            )
          }
          .map(_ => msg.committableOffset)
      case _ =>
        Future.successful(msg.committableOffset)
    }
  }
}
