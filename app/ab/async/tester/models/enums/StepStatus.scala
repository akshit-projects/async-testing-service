package ab.async.tester.models.enums

import io.circe.{Decoder, Encoder}


sealed trait StepStatus

object StepStatus {
  case object SUCCESS extends StepStatus
  case object ERROR extends StepStatus

  implicit val encodeStepStatus: Encoder[StepStatus] = Encoder.encodeString.contramap[StepStatus] {
    case SUCCESS => "SUCCESS"
    case ERROR   => "ERROR"
  }

  implicit val decodeStepStatus: Decoder[StepStatus] = Decoder.decodeString.emap {
    case "SUCCESS" => Right(SUCCESS)
    case "ERROR"   => Right(ERROR)
    case other     => Left(s"Unknown StepStatus: $other")
  }
}