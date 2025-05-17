package ab.async.tester.converters

import ab.async.tester.models.resource._
import io.circe.parser._
import io.circe.syntax._
import org.bson.BsonDocument
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.Logger

/**
 * Utility class for converting between ResourceConfig and MongoDB Document
 */
object ResourceConverters {
  private val logger = Logger(getClass)
  
  /**
   * Converts a ResourceConfig to a MongoDB Document
   *
   * @param resource The resource configuration to convert
   * @return A MongoDB Document
   */
  def toDocument(resource: ResourceConfig): Document = {
    try {
      val flowJson = resource.asJson.noSpaces
      val doc = BsonDocument.parse(flowJson)
      
      // Get ID from resource
      val id = resource.getId
      val bsonDoc = doc.toBsonDocument

      bsonDoc.remove("id")
      if (id.isEmpty) {
        bsonDoc.put("_id", new org.bson.BsonString(new ObjectId().toString))
      }

      val typeName = resource match {
        case _: KafkaConfig => "kafka"
        case _: APISchemaConfig => "api-schema"
        case _: CacheConfig => "cache" 
        case _: SQLDBConfig => "db"
        case _ => throw new IllegalArgumentException(s"Unsupported resource type: ${resource.getClass.getSimpleName}")
      }
      bsonDoc.put("type", new org.bson.BsonString(typeName))
      
      // Convert back to Document
      bsonDoc
    } catch {
      case e: Exception =>
        logger.error(s"Error converting ResourceConfig to Document: ${e.getMessage}", e)
        throw e
    }
  }
  
  /**
   * Converts a MongoDB Document to a ResourceConfig
   *
   * @param doc The MongoDB Document to convert
   * @return An Option containing the ResourceConfig, or None if conversion failed
   */
  def fromDocument(doc: Document): Option[ResourceConfig] = {
    try {
      if (doc == null) return None
      
      // Convert _id to id for proper JSON deserialization
      val json = if (doc.contains("_id")) {
        val idValue = doc.getString("_id")
        val modifiedDoc = doc.toBsonDocument
        modifiedDoc.remove("_id")
        modifiedDoc.put("id", new org.bson.BsonString(idValue))
        modifiedDoc.toJson
      } else {
        doc.toJson()
      }
      
      // Parse using circe
      decode[ResourceConfig](json) match {
        case Right(resource) => Some(resource)
        case Left(error) =>
          logger.error(s"Error decoding ResourceConfig: ${error.getMessage}", error)
          None
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error converting Document to ResourceConfig: ${e.getMessage}", e)
        None
    }
  }
  
  /**
   * Tries to detect the type of resource from a document
   * 
   * @param doc The MongoDB Document
   * @return The detected resource type as a string, or None if not detected
   */
  def detectResourceType(doc: Document): Option[String] = {
    try {
      if (doc.contains("type")) {
        Some(doc.getString("type"))
      } else {
        // Try to guess based on fields
        if (doc.contains("brokers")) {
          Some("kafka")
        } else if (doc.contains("endpoint")) {
          Some("api-schema")
        } else if (doc.contains("host") && doc.contains("port")) {
          if (doc.contains("jdbcUrl")) {
            Some("db")
          } else {
            Some("cache")
          }
        } else {
          None
        }
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error detecting resource type: ${e.getMessage}", e)
        None
    }
  }
}