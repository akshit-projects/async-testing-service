package ab.async.tester.converters

import ab.async.tester.models.flow.Floww
import io.circe.parser._
import io.circe.syntax._
import org.bson.BsonDocument
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.Logger

/**
 * JSON formats and codecs for Flow-related models
 */
object FlowConverters {
  private val logger = Logger(getClass)
//  implicit val flowProvider = Macros.createCodecIgnoreNone[Flow]()
//  private val codecRegistry = fromRegistries(fromProviders(flowProvider), DEFAULT_CODEC_REGISTRY)


  /**
   * Converts a Flow object to a MongoDB Document
   */
  def flowToDocument(flow: Floww): Document = {
    try {
      val flowJson = flow.asJson.noSpaces
      val doc = BsonDocument.parse(flowJson)
      doc
    } catch {
      case e: Exception =>
        logger.error(s"Error converting flow to document: ${e.getMessage}", e)
        throw e
    }
  }
  
  /**
   * Converts a MongoDB Document to a Flow object
   */
  def documentToFlow(doc: Document): Option[Floww] = {
    try {
      // First create a copy with "_id" converted to "id"
      val json = if (doc.contains("_id")) {
        val idValue = doc.getString("_id")
        val modifiedDoc = doc.toBsonDocument
        modifiedDoc.remove("_id")
        modifiedDoc.put("id", new org.bson.BsonString(idValue))
        modifiedDoc.toJson
      } else {
        doc.toJson()
      }
      
      // Parse JSON to Flow using circe
      decode[Floww](json) match {
        case Right(flow) => Some(flow)
        case Left(error) => 
          logger.error(s"Error decoding flow: ${error.getMessage}")
          None
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error converting document to flow: ${e.getMessage}", e)
        None
    }
  }
} 