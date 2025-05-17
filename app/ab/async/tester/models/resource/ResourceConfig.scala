package ab.async.tester.models.resource

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}

trait ResourceConfig {

  val `namespace`: String

  val name: String

  val group: String

  val id: String

  def getId: String

  def setId(newId: String): ResourceConfig

  def getType: String

  def getNamespace: String = `namespace`
}

object ResourceConfig {
  implicit val configEncoder: Encoder[ResourceConfig] = Encoder.instance {
    case x: KafkaConfig => KafkaConfig.kafkaConfigEncoder(x)
    case x: APISchemaConfig => APISchemaConfig.apiSchemaConfigEncoder(x)
    case x: CacheConfig => CacheConfig.cacheConfigEncoder(x)
    case x: SQLDBConfig => SQLDBConfig.dbConfigEncoder(x)
  }

  implicit val configDecoder: Decoder[ResourceConfig] = (c: HCursor) => {
    c.downField("type").as[String].flatMap {
      case "kafka" => c.as[KafkaConfig]
      case "api-schema" => c.as[APISchemaConfig]
      case "cache" => c.as[CacheConfig]
      case "db" => c.as[SQLDBConfig]
      case other => Left(DecodingFailure(s"Unknown type: $other", c.history))
    }
  }
}
