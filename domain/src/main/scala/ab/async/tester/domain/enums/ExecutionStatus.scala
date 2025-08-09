package ab.async.tester.domain.enums

import io.circe.{Decoder, Encoder}

sealed trait ExecutionStatus

object ExecutionStatus {
  case object Todo extends ExecutionStatus
  case object InProgress extends ExecutionStatus
  case object Completed extends ExecutionStatus
  case object Failed extends ExecutionStatus
  
  implicit val encoder: Encoder[ExecutionStatus] = Encoder.encodeString.contramap {
    case Todo => "TODO"
    case InProgress => "IN_PROGRESS"
    case Completed => "COMPLETED"
    case Failed => "FAILED"
  }
  
  implicit val decoder: Decoder[ExecutionStatus] = Decoder.decodeString.emap {
    case "TODO" => Right(Todo) 
    case "IN_PROGRESS" => Right(InProgress)
    case "COMPLETED" => Right(Completed)
    case "FAILED" => Right(Failed)
    case other => Left(s"Unknown execution status: $other")
  }
} 