package ab.async.tester.exceptions

/** Exception thrown when validation of input data fails
  * @param message
  *   error message explaining the validation failure
  */
case class ValidationException(message: String)
    extends RuntimeException(message)
