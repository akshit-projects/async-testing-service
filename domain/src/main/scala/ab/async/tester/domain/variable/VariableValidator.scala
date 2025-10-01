package ab.async.tester.domain.variable

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

/**
 * Validates variable values against their data types
 */
object VariableValidator {

  /**
   * Validates a single value against a data type
   */
  def validateValue(value: String, dataType: VariableDataType): VariableValidationResult = {
    val errors = scala.collection.mutable.ListBuffer[String]()

    dataType match {
      case VariableDataType.STRING =>
        // String is always valid
        
      case VariableDataType.INTEGER =>
        Try(value.toInt) match {
          case Failure(_) => errors += s"'$value' is not a valid integer"
          case Success(_) =>
        }
        
      case VariableDataType.DOUBLE =>
        Try(value.toDouble) match {
          case Failure(_) => errors += s"'$value' is not a valid double"
          case Success(_) =>
        }
        
      case VariableDataType.BOOLEAN =>
        value.toLowerCase match {
          case "true" | "false" =>
          case _ => errors += s"'$value' is not a valid boolean (must be 'true' or 'false')"
        }
        
      case VariableDataType.DATE =>
        Try(LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)) match {
          case Failure(_) => errors += s"'$value' is not a valid date (expected format: YYYY-MM-DD)"
          case Success(_) => // Valid
        }

    }

    VariableValidationResult(
      isValid = errors.isEmpty,
      errors = errors.toList
    )
  }
}
