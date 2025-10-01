package ab.async.tester.domain.variable

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
 * Represents a variable that can be used in a flow
 */
case class FlowVariable(
  name: String,
  `type`: VariableDataType,
  defaultValue: Option[String] = None,
  description: Option[String] = None,
)

/**
 * Supported data types for flow variables
 */
sealed trait VariableDataType

object VariableDataType {
  case object STRING extends VariableDataType
  case object INTEGER extends VariableDataType
  case object DOUBLE extends VariableDataType
  case object BOOLEAN extends VariableDataType
  case object DATE extends VariableDataType

  implicit val variableDataTypeEncoder: Encoder[VariableDataType] = Encoder.encodeString.contramap {
    case STRING => "string"
    case INTEGER => "integer"
    case DOUBLE => "double"
    case BOOLEAN => "boolean"
    case DATE => "date"
  }

  implicit val variableDataTypeDecoder: Decoder[VariableDataType] = Decoder.decodeString.emap {
    case "string" => Right(STRING)
    case "integer" => Right(INTEGER)
    case "double" => Right(DOUBLE)
    case "boolean" => Right(BOOLEAN)
    case "date" => Right(DATE)
    case other => Left(s"Unknown variable data type: $other")
  }
}

/**
 * Runtime values provided for flow variables
 */
case class VariableValue(
                          name: String,
                          value: String,
                          `type`: VariableDataType
)

/**
 * Validation result for variable values
 */
case class VariableValidationResult(
  isValid: Boolean,
  errors: List[String] = List.empty
)

object FlowVariable {
  implicit val flowVariableEncoder: Encoder[FlowVariable] = deriveEncoder
  implicit val flowVariableDecoder: Decoder[FlowVariable] = deriveDecoder
}

object VariableValue {
  implicit val variableValueEncoder: Encoder[VariableValue] = deriveEncoder
  implicit val variableValueDecoder: Decoder[VariableValue] = deriveDecoder
}
