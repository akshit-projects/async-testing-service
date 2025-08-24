package ab.async.tester.domain.resource

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
case class SQLDBConfig(id: String,
                       `namespace`: Option[String],
                       group: Option[String],
                       `type`: String,
                       name: String,
                       dbUrl: String,
                       username: String,
                       password: String) extends ResourceConfig {

  override def getId: String = id

  override def getType: String = "sql-db"

  override def setId(newId: String): ResourceConfig = this.copy(id = newId)
}

object SQLDBConfig {
  implicit val dbConfigEncoder: Encoder[SQLDBConfig] = deriveEncoder
  implicit val dbConfigDecoder: Decoder[SQLDBConfig] = deriveDecoder
}

