package ab.async.tester.library.utils

import io.circe.parser._
import io.circe.{Decoder, Encoder, Json}
import play.api.Logger
import play.api.libs.json.{JsValue, Json => PlayJson}

/**
 * Utility for decoding JSON strings into case classes
 */
object DecodingUtils {
  private val logger = Logger(this.getClass)

  /**
   * Decodes a JSON string into a case class and logs any errors
   *
   * @param jsonString the JSON string to decode
   * @tparam T the target type to decode into
   * @return the decoded object
   * @throws RuntimeException if the JSON string cannot be decoded
   */
  def decodeWithErrorLogs[T](jsonString: String)(implicit decoder: Decoder[T], logger: Logger): T = {
    decode[T](jsonString) match {
      case Right(result) => result
      case Left(error) =>
        logger.error(s"Error decoding JSON: ${error.getMessage}")
        logger.debug(s"JSON string that failed to decode: $jsonString")
        throw new RuntimeException(s"Invalid JSON format: ${error.getMessage}")
    }
  }
  
  /**
   * Decodes a Play JsValue into a case class
   */
  def decodeJsValue[T](jsValue: JsValue)(implicit decoder: Decoder[T], logger: Logger): T = {
    decodeWithErrorLogs[T](jsValue.toString())
  }
  
  /**
   * Converts a Play JsValue to a Circe Json
   */
  def playToCirce(jsValue: JsValue): Json = {
    parse(PlayJson.stringify(jsValue)) match {
      case Right(json) => json
      case Left(error) => 
        logger.error(s"Error converting Play JSON to Circe: ${error.getMessage}")
        throw new RuntimeException(s"JSON conversion error: ${error.getMessage}")
    }
  }
  
  /**
   * Converts a Circe Json to a Play JsValue
   */
  def circeToPlay(json: Json): JsValue = {
    PlayJson.parse(json.noSpaces)
  }
  
  /**
   * Converts a case class to a JSON string
   */
  def encodeToJsonString[T](obj: T)(implicit encoder: Encoder[T]): String = {
    io.circe.syntax.EncoderOps(obj).asJson.noSpaces
  }
}