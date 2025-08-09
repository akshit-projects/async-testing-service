package ab.async.tester.library.utils

import io.circe.parser._
import io.circe.{Decoder, DecodingFailure, ParsingFailure}
import play.api.mvc.Results._
import io.circe.syntax._
import play.api.mvc.{AnyContent, Request, Result}

import scala.concurrent.ExecutionContext

object JsonParsers {

  case class ParseError(message: String)
  case class ValidationError(message: String)

  /** Decode request JSON into T and map errors to HTTP Results. */
  def parseJsonBody[T](request: Request[AnyContent])(implicit d: Decoder[T], ec: ExecutionContext): Either[Result, T] = {
    request.body.asJson match {
      case Some(json) =>
        decode[T](json.toString) match {
          case Right(t) => Right(t)
          case Left(e: ParsingFailure) => Left(BadRequest(Map("error" -> s"Malformed JSON: ${e.message}").asJsonNoSpaces))
          case Left(e: DecodingFailure) => Left(BadRequest(Map("error" -> s"Invalid payload: $e").asJsonNoSpaces))
          case Left(e) => Left(BadRequest(Map("error" -> s"Unknown JSON error: ${e.getMessage}").asJsonNoSpaces))
        }
      case None =>
        Left(BadRequest(Map("error" -> "Missing JSON body").asJsonNoSpaces))
    }
  }

  implicit class ResultHelpers(val m: Map[String, String]) {
    def asJsonNoSpaces: String = {
      m.asJson.noSpaces
    }
  }
}
