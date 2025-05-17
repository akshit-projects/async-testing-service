package ab.async.tester.models.execution

import ab.async.tester.models.step.{StepError, StepResponse}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

/**
 * Status update type for execution updates
 */
sealed trait StatusUpdateType

object StatusUpdateType {
  case object Info extends StatusUpdateType
  case object Error extends StatusUpdateType
  case object StepUpdate extends StatusUpdateType
  case object Complete extends StatusUpdateType
  
  implicit val encoder: Encoder[StatusUpdateType] = Encoder.encodeString.contramap {
    case Info => "info"
    case Error => "error"
    case StepUpdate => "step_update"
    case Complete => "complete"
  }
  
  implicit val decoder: Decoder[StatusUpdateType] = Decoder.decodeString.emap {
    case "info" => Right(Info)
    case "error" => Right(Error)
    case "step_update" => Right(StepUpdate)
    case "complete" => Right(Complete)
    case other => Left(s"Unknown status update type: $other")
  }
}

/**
 * Model for execution status updates
 */
case class ExecutionStatusUpdate(
  `type`: StatusUpdateType,
  stepResponse: Option[StepResponse] = None,
  message: String = "",
  errorCode: Option[String] = None
)

object ExecutionStatusUpdate {
  implicit val encoder: Encoder[ExecutionStatusUpdate] = deriveEncoder
  implicit val decoder: Decoder[ExecutionStatusUpdate] = deriveDecoder
  
  def createError(message: String, errorCode: String): ExecutionStatusUpdate = {
    ExecutionStatusUpdate(
      `type` = StatusUpdateType.Error,
      message = message,
      errorCode = Some(errorCode)
    )
  }
  
  def createStepUpdate(stepResponse: StepResponse): ExecutionStatusUpdate = {
    ExecutionStatusUpdate(
      `type` = StatusUpdateType.StepUpdate,
      stepResponse = Some(stepResponse)
    )
  }
  
  def createInfo(message: String): ExecutionStatusUpdate = {
    ExecutionStatusUpdate(
      `type` = StatusUpdateType.Info,
      message = message
    )
  }
  
  def createComplete(message: String): ExecutionStatusUpdate = {
    ExecutionStatusUpdate(
      `type` = StatusUpdateType.Complete,
      message = message
    )
  }
} 