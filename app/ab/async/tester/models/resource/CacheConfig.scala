package ab.async.tester.models.resource

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

case class CacheConfig(id: String,
                       `namespace`: String,
                       group: String,
                       name: String,
                       url: String,
                       password: String) extends ResourceConfig {
  override def getId: String = id

  override def getType: String = "cache"

  override def setId(newId: String): ResourceConfig = this.copy(id = newId)
}

object CacheConfig {
  implicit val cacheConfigEncoder: Encoder[CacheConfig] = deriveEncoder
  implicit val cacheConfigDecoder: Decoder[CacheConfig] = deriveDecoder
}

