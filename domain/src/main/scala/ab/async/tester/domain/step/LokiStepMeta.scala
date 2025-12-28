package ab.async.tester.domain.step

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Step metadata for Loki log search
  */
case class LokiStepMeta(
    resourceId: String,
    namespace: String,
    startTime: Option[Long] = None, // Unix timestamp in milliseconds
    endTime: Option[Long] = None, // Unix timestamp in milliseconds
    relativeTime: Option[String] = None, // e.g., "5m", "15m", "1h"
    labels: Map[
      String,
      String
    ], // Label matchers e.g., {"app": "api-service", "env": "prod"}
    containsPatterns: List[String] = List.empty, // Must contain all patterns
    notContainsPatterns: List[String] = List.empty,
    limit: Int = 1000
) extends StepMeta

object LokiStepMeta {
  implicit val encoder: Encoder[LokiStepMeta] = deriveEncoder[LokiStepMeta]
  implicit val decoder: Decoder[LokiStepMeta] = deriveDecoder[LokiStepMeta]
}
