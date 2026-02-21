package ab.async.tester.library.clients.slack

import ab.async.tester.domain.execution.Execution
import ab.async.tester.domain.enums.ExecutionStatus
import play.api.libs.ws.{StandaloneWSClient, WSClient}
import play.api.libs.json.Json

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SlackClient @Inject() (wsClient: StandaloneWSClient)(implicit ec: ExecutionContext) {

  def sendMessage(webhookUrl: String, text: String): Future[Unit] = {
    wsClient.url(webhookUrl)
      .post(Json.obj("text" -> text))
      .map { response =>
        if (response.status >= 200 && response.status < 300) {
          ()
        } else {
          throw new Exception(
            s"Failed to send Slack message: ${response.status} ${response.body}"
          )
        }
      }
  }

  def sendExecutionReport(
      webhookUrl: String,
      execution: Execution,
      flowName: String
  ): Future[Unit] = {
    val statusEmoji = execution.status match {
      case ExecutionStatus.Completed => "✅"
      case ExecutionStatus.Failed    => "❌"
      case _                         => "⏳"
    }

    val blocks = Json.arr(
      Json.obj(
        "type" -> "header",
        "text" -> Json.obj(
          "type" -> "plain_text",
          "text" -> s"$statusEmoji Flow Execution Report",
          "emoji" -> true
        )
      ),
      Json.obj(
        "type" -> "section",
        "fields" -> Json.arr(
          Json.obj("type" -> "mrkdwn", "text" -> s"*Flow:* $flowName"),
          Json.obj(
            "type" -> "mrkdwn",
            "text" -> s"*Execution ID:* ${execution.id}"
          ),
          Json.obj(
            "type" -> "mrkdwn",
            "text" -> s"*Status:* ${execution.status}"
          ),
          Json.obj(
            "type" -> "mrkdwn",
            "text" -> s"*Started At:* ${execution.startedAt}"
          )
        )
      ),
      Json.obj(
        "type" -> "section",
        "text" -> Json.obj(
          "type" -> "mrkdwn",
          "text" -> s"*View Details:* <http://localhost:3000/executions/${execution.id}|Click here>"
        )
      )
    )

    wsClient.url(webhookUrl)
      .post(Json.obj("blocks" -> blocks))
      .map(_ => ())
  }
}
