package ab.async.tester.domain.resource

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class CacheConfig(id: String,
                       `namespace`: Option[String],
                       group: Option[String],
                       `type`: String,
                       name: String,
                       url: String,
                       port: Int,
                       password: Option[String],
                       database: Option[Int]) extends ResourceConfig {
  override def getId: String = id

  override def getType: String = "cache"

  override def setId(newId: String): ResourceConfig = this.copy(id = newId)
}

object CacheConfig {
  implicit val cacheConfigEncoder: Encoder[CacheConfig] = deriveEncoder
  implicit val cacheConfigDecoder: Decoder[CacheConfig] = deriveDecoder
}

