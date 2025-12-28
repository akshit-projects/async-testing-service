package ab.async.tester.domain.kafka

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Search pattern configuration for Kafka message filtering
  */
case class KafkaSearchPattern(
    matchType: String, // "exact" for string contains, "jsonpath" for JSON queries
    pattern: String, // Search string or JSONPath expression (e.g., "$.data.userId")
    matchAll: Boolean = true // Return all matches (true) vs first match (false)
)

object KafkaSearchPattern {
  implicit val encoder: Encoder[KafkaSearchPattern] =
    deriveEncoder[KafkaSearchPattern]
  implicit val decoder: Decoder[KafkaSearchPattern] =
    deriveDecoder[KafkaSearchPattern]
}
