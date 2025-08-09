package ab.async.tester.domain.enums

import io.circe.{Decoder, Encoder}

sealed trait ExecutionStatusType
object ExecutionStatusType {
  case object EXECUTION_STATUS_ERROR extends ExecutionStatusType
  case object EXECUTION_STATUS_MESSAGE extends ExecutionStatusType
  case object EXECUTION_STATUS_SR extends ExecutionStatusType

  implicit val encodeExecutionStatusType: Encoder[ExecutionStatusType] = Encoder.encodeString.contramap[ExecutionStatusType] {
    case EXECUTION_STATUS_ERROR   => "ERROR"
    case EXECUTION_STATUS_MESSAGE => "MESSAGE"
    case EXECUTION_STATUS_SR      => "SR"
  }

  implicit val decodeExecutionStatusType: Decoder[ExecutionStatusType] = Decoder.decodeString.emap {
    case "ERROR"   => Right(EXECUTION_STATUS_ERROR)
    case "MESSAGE" => Right(EXECUTION_STATUS_MESSAGE)
    case "SR"      => Right(EXECUTION_STATUS_SR)
    case other     => Left(s"Unknown ExecutionStatusType: $other")
  }
}