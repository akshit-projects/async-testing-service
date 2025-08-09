package ab.async.tester.domain.resource

import io.circe.jawn.decode
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}

trait ResourceConfig {

  val `namespace`: Option[String]

  val name: String

  val group: Option[String]

  val id: String

  def getId: String

  def setId(newId: String): ResourceConfig

  def getType: String

  def getNamespace: Option[String] = `namespace`
}

object ResourceConfig {
  implicit val configEncoder: Encoder[ResourceConfig] = Encoder.instance {
    case x: KafkaResourceConfig => KafkaResourceConfig.kafkaConfigEncoder(x)
    case x: APISchemaConfig => APISchemaConfig.apiSchemaConfigEncoder(x)
    case x: CacheConfig => CacheConfig.cacheConfigEncoder(x)
    case x: SQLDBConfig => SQLDBConfig.dbConfigEncoder(x)
  }

  implicit val configDecoder: Decoder[ResourceConfig] = (c: HCursor) => {
    c.downField("type").as[String].flatMap {
      case "kafka" => c.as[KafkaResourceConfig]
      case "api-schema" => c.as[APISchemaConfig]
      case "cache" => c.as[CacheConfig]
      case "db" => c.as[SQLDBConfig]
      case other => Left(DecodingFailure(s"Unknown type: $other", c.history))
    }
  }

  def fromDb(serialisedPayload: String): ResourceConfig = {
    decode[ResourceConfig](serialisedPayload)(configDecoder) match {
      case Left(ex) => throw ex
      case Right(v) => v
    }
  }
}
