package ab.async.tester.exceptions

object CommonExceptions {

  case class DecodingError(error: String) extends RuntimeException(error)
}
