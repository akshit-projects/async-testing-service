package ab.async.tester.converters

import ab.async.tester.models.execution.{Execution, ExecutionFlow, ExecutionStatus}
import io.circe.parser._
import io.circe.syntax._
import org.bson.BsonDocument
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.Logger

/**
 * Utility class for converting between Execution and MongoDB Document
 */
object ExecutionConverters {
  private val logger = Logger(getClass)
  
  /**
   * Converts an Execution to a MongoDB Document
   *
   * @param execution The execution to convert
   * @return A MongoDB Document
   */
  def toDocument(execution: Execution): Document = {
    try {
      val executionJson = execution.asJson.noSpaces
      val doc = BsonDocument.parse(executionJson)
      
      // Set ID if present, otherwise generate new one
      val bsonDoc = doc.toBsonDocument
      bsonDoc.remove("id")
      
      execution.id match {
        case Some(id) if id.nonEmpty => 
          bsonDoc.put("_id", new org.bson.BsonString(id))
        case _ => 
          bsonDoc.put("_id", new org.bson.BsonString(new ObjectId().toString))
      }
      
      // Convert status to string
      val statusStr = execution.status match {
        case ExecutionStatus.Todo => "TODO"
        case ExecutionStatus.InProgress => "IN_PROGRESS"
        case ExecutionStatus.Completed => "COMPLETED"
        case ExecutionStatus.Failed => "FAILED"
      }
      
      bsonDoc.put("status", new org.bson.BsonString(statusStr))
      
      // Convert back to Document
      Document(bsonDoc)
    } catch {
      case e: Exception =>
        logger.error(s"Error converting Execution to Document: ${e.getMessage}", e)
        throw e
    }
  }
  
  /**
   * Converts a MongoDB Document to an Execution
   *
   * @param doc The MongoDB Document to convert
   * @return An Option containing the Execution, or None if conversion failed
   */
  def fromDocument(doc: Document): Option[Execution] = {
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
      decode[Execution](json) match {
        case Right(execution) => Some(execution)
        case Left(error) =>
          logger.error(s"Error decoding Execution: ${error.getMessage}", error)
          None
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error converting Document to Execution: ${e.getMessage}", e)
        None
    }
  }
} 