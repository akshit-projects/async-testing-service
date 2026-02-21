package ab.async.tester.domain.alert

import ab.async.tester.domain.enums.ReportingCallbackType
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps

case class ReportingConfig(
  callbackType: ReportingCallbackType,
  callbackConfig: ReportingCallbackConfig
)

trait ReportingCallbackConfig {
  val `type`: String
}

case class SlackReportingCallbackConfig(
    override val `type`: String,
  webhookUrl: String,
  channelId: Option[String] = None
) extends ReportingCallbackConfig

object SlackReportingCallbackConfig {
  implicit val decoder: Decoder[SlackReportingCallbackConfig] = deriveDecoder
  implicit val encoder: Encoder[SlackReportingCallbackConfig] = deriveEncoder
}

object ReportingCallbackConfig {

  implicit val decoder: Decoder[ReportingCallbackConfig] =
    Decoder.instance { c =>
      c.downField("type").as[String].flatMap {

        case "slack" =>
          c.as[SlackReportingCallbackConfig]

        case other =>
          Left(DecodingFailure(s"Unknown ReportingCallbackConfig type: $other", c.history))
      }
    }

  implicit val encoder: Encoder[ReportingCallbackConfig] =
    Encoder.instance {
      case s: SlackReportingCallbackConfig =>
        s.asJson
    }
}

object ReportingConfig {
  implicit val decoder: Decoder[ReportingConfig] = deriveDecoder
  implicit val encoder: Encoder[ReportingConfig] = deriveEncoder
}



