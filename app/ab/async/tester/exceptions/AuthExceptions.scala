package ab.async.tester.exceptions

object AuthExceptions {

  case class InvalidAuthException() extends RuntimeException(
    "Unable to authenticate user"
  )
}
