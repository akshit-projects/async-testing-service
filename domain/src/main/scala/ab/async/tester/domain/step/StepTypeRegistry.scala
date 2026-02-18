package ab.async.tester.domain.step

import ab.async.tester.domain.enums.StepType
import ab.async.tester.domain.step.metas.{ConditionStepMeta, DelayStepMeta, HttpStepMeta, KafkaPublishMeta, KafkaSubscribeMeta, LokiStepMeta, RedisStepMeta, SqlStepMeta}
import ab.async.tester.domain.step.metas.ConditionStepMeta._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/** Metadata for a step type containing configuration and discriminator
  * information
  * @param stepType
  *   The step type enum
  * @param identifier
  *   Primary string identifier for this step type
  * @param aliases
  *   Alternative string identifiers that map to this step type
  * @param hasResource
  *   Whether this step type uses a resource
  * @param discriminatorFields
  *   JSON fields used to identify this step type during decoding
  */
case class StepTypeMetadata(
    stepType: StepType,
    identifier: String,
    aliases: List[String] = List.empty,
    hasResource: Boolean,
    discriminatorFields: List[String],
    decoder: Decoder[_ <: StepMeta],
    encoder: Encoder[_ <: StepMeta]
)

/** Centralized registry for step type metadata
  *
  * This registry provides a single source of truth for step type configuration,
  * making it easy to add new step types without modifying multiple files.
  */
object StepTypeRegistry {
  // Individual encoders and decoders
  implicit val httpStepMetaDecoder: Decoder[HttpStepMeta] =
    deriveDecoder[HttpStepMeta]
  implicit val kafkaSubscribeMetaDecoder: Decoder[KafkaSubscribeMeta] =
    deriveDecoder[KafkaSubscribeMeta]
  implicit val kafkaPublishMetaDecoder: Decoder[KafkaPublishMeta] =
    deriveDecoder[KafkaPublishMeta]
  implicit val delayStepMetaDecoder: Decoder[DelayStepMeta] =
    deriveDecoder[DelayStepMeta]
  implicit val sqlStepMetaDecoder: Decoder[SqlStepMeta] =
    deriveDecoder[SqlStepMeta]
  implicit val redisStepMetaDecoder: Decoder[RedisStepMeta] =
    deriveDecoder[RedisStepMeta]
  implicit val lokiStepMetaDecoder: Decoder[LokiStepMeta] =
    deriveDecoder[LokiStepMeta]
  implicit val conditionStepMetaDecoder: Decoder[ConditionStepMeta] =
    deriveDecoder[ConditionStepMeta]

  implicit val httpStepMetaEncoder: Encoder[HttpStepMeta] =
    deriveEncoder[HttpStepMeta]
  implicit val kafkaSubscribeMetaEncoder: Encoder[KafkaSubscribeMeta] =
    deriveEncoder[KafkaSubscribeMeta]
  implicit val kafkaPublishMetaEncoder: Encoder[KafkaPublishMeta] =
    deriveEncoder[KafkaPublishMeta]
  implicit val delayStepMetaEncoder: Encoder[DelayStepMeta] =
    deriveEncoder[DelayStepMeta]
  implicit val sqlStepMetaEncoder: Encoder[SqlStepMeta] =
    deriveEncoder[SqlStepMeta]
  implicit val redisStepMetaEncoder: Encoder[RedisStepMeta] =
    deriveEncoder[RedisStepMeta]
  implicit val lokiStepMetaEncoder: Encoder[LokiStepMeta] =
    deriveEncoder[LokiStepMeta]
  implicit val conditionStepMetaEncoder: Encoder[ConditionStepMeta] =
    deriveEncoder[ConditionStepMeta]

  /** Registry mapping step types to their metadata */
  private val registry: Map[StepType, StepTypeMetadata] = Map(
    StepType.HttpRequest -> StepTypeMetadata(
      stepType = StepType.HttpRequest,
      identifier = "http",
      aliases = List.empty,
      hasResource = true,
      discriminatorFields = List("body", "method"),
      decoder = httpStepMetaDecoder,
      encoder = httpStepMetaEncoder
    ),
    StepType.KafkaPublish -> StepTypeMetadata(
      stepType = StepType.KafkaPublish,
      identifier = "kafka_publish",
      aliases = List("kafka_pub"),
      hasResource = true,
      discriminatorFields = List("messages"),
      decoder = kafkaPublishMetaDecoder,
      encoder = kafkaPublishMetaEncoder
    ),
    StepType.KafkaSubscribe -> StepTypeMetadata(
      stepType = StepType.KafkaSubscribe,
      identifier = "kafka_subscribe",
      aliases = List("kafka_sub"),
      hasResource = true,
      discriminatorFields = List("groupId"),
      decoder = kafkaSubscribeMetaDecoder,
      encoder = kafkaSubscribeMetaEncoder
    ),
    StepType.Delay -> StepTypeMetadata(
      stepType = StepType.Delay,
      identifier = "delay",
      aliases = List.empty,
      hasResource = false,
      discriminatorFields = List("delayMs"),
      decoder = delayStepMetaDecoder,
      encoder = delayStepMetaEncoder
    ),
    StepType.SqlQuery -> StepTypeMetadata(
      stepType = StepType.SqlQuery,
      identifier = "sql-db",
      aliases = List.empty,
      hasResource = true,
      discriminatorFields = List("query"),
      decoder = sqlStepMetaDecoder,
      encoder = sqlStepMetaEncoder
    ),
    StepType.RedisOperation -> StepTypeMetadata(
      stepType = StepType.RedisOperation,
      identifier = "cache",
      aliases = List.empty,
      hasResource = true,
      discriminatorFields = List("operation", "key"),
      decoder = redisStepMetaDecoder,
      encoder = redisStepMetaEncoder
    ),
    StepType.LokiLogSearch -> StepTypeMetadata(
      stepType = StepType.LokiLogSearch,
      identifier = "loki_logs",
      aliases = List("loki"),
      hasResource = true,
      discriminatorFields = List("namespace", "labels"),
      decoder = lokiStepMetaDecoder,
      encoder = lokiStepMetaEncoder
    ),
    StepType.Condition -> StepTypeMetadata(
      stepType = StepType.Condition,
      identifier = "condition",
      aliases = List("if"),
      hasResource = false,
      discriminatorFields = List("branches"),
      decoder = conditionStepMetaDecoder,
      encoder = conditionStepMetaEncoder
    )

    // TODO add script step
  )

  /** Reverse mapping from identifier strings to step types */
  private val identifierToStepType: Map[String, StepType] = {
    registry.flatMap { case (stepType, metadata) =>
      (metadata.identifier :: metadata.aliases).map(id =>
        id.toLowerCase -> stepType
      )
    }
  }

  /** Mapping from discriminator field sets to step types Used for JSON decoding
    * to determine which step type to use
    */
  private val discriminatorToStepType: Map[Set[String], StepType] = {
    registry.map { case (stepType, metadata) =>
      metadata.discriminatorFields.toSet -> stepType
    }
  }

  /** Get metadata for a step type
    * @param stepType
    *   The step type
    * @return
    *   The metadata for the step type
    * @throws NoSuchElementException
    *   if the step type is not registered
    */
  def getMetadata(stepType: StepType): StepTypeMetadata = {
    registry.getOrElse(
      stepType,
      throw new NoSuchElementException(
        s"No metadata found for step type: $stepType"
      )
    )
  }

  /** Get step type from identifier string
    * @param identifier
    *   The string identifier (case-insensitive)
    * @return
    *   Some(StepType) if found, None otherwise
    */
  def fromIdentifier(identifier: String): Option[StepType] = {
    identifierToStepType.get(identifier.toLowerCase)
  }

  /** Get step type from discriminator fields Used during JSON decoding to
    * determine step type
    * @param fields
    *   Set of field names present in the JSON
    * @return
    *   Some(StepType) if a match is found, None otherwise
    */
  def fromDiscriminatorFields(fields: Set[String]): Option[StepType] = {
    // Try to find exact match first
    discriminatorToStepType.get(fields).orElse {
      // If no exact match, find step type where all discriminator fields are present
      discriminatorToStepType
        .find { case (requiredFields, _) =>
          requiredFields.subsetOf(fields)
        }
        .map(_._2)
    }
  }

  /** Get all registered step types
    * @return
    *   List of all step types in the registry
    */
  def allStepTypes: List[StepType] = registry.keys.toList

  /** Check if a step type uses resources
    * @param stepType
    *   The step type to check
    * @return
    *   true if the step type uses resources, false otherwise
    */
  def hasResource(stepType: StepType): Boolean = {
    getMetadata(stepType).hasResource
  }

  /** Get all identifiers (primary + aliases) for a step type
    * @param stepType
    *   The step type
    * @return
    *   List of all valid identifiers
    */
  def getAllIdentifiers(stepType: StepType): List[String] = {
    val metadata = getMetadata(stepType)
    metadata.identifier :: metadata.aliases
  }
}
