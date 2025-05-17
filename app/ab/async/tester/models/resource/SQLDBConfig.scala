package ab.async.tester.models.resource

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

case class SQLDBConfig(id: String,
                       `namespace`: String,
                       group: String,
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

