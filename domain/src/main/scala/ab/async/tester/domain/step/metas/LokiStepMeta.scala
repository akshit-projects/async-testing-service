package ab.async.tester.domain.step.metas

import ab.async.tester.domain.enums.StepType
import ab.async.tester.domain.step.StepMeta

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
) extends StepMeta {

  override def stepType: StepType = StepType.LokiLogSearch
}
