package ab.async.tester.domain.enums

import io.circe.{Decoder, Encoder}

sealed trait ExecutionUpdateType

object ExecutionUpdateType {
  case object STEP_UPDATE extends ExecutionUpdateType
  case object MESSAGE extends ExecutionUpdateType
  case object TOAST extends ExecutionUpdateType

  implicit val encodeStepStatus: Encoder[ExecutionUpdateType] = Encoder.encodeString.contramap[ExecutionUpdateType] {
    case STEP_UPDATE   => "STEP_UPDATE"
    case MESSAGE => "MESSAGE"
    case TOAST   => "TOAST"
  }

  implicit val decodeStepStatus: Decoder[ExecutionUpdateType] = Decoder.decodeString.emap {
    case "STEP_UPDATE" => Right(STEP_UPDATE)
    case "MESSAGE" => Right(MESSAGE)
    case "TOAST"   => Right(TOAST)
    case other     => Left(s"Unknown ExecutionUpdateType: $other")
  }
}