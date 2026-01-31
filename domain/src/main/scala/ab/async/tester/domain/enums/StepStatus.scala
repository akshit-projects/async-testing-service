package ab.async.tester.domain.enums

import io.circe.{Decoder, Encoder}

sealed trait StepStatus

object StepStatus {
  case object IN_PROGRESS extends StepStatus
  case object SUCCESS extends StepStatus
  case object ERROR extends StepStatus
  case object PENDING extends StepStatus

  implicit val encodeStepStatus: Encoder[StepStatus] =
    Encoder.encodeString.contramap[StepStatus] {
      case IN_PROGRESS => "IN_PROGRESS"
      case SUCCESS     => "SUCCESS"
      case ERROR       => "ERROR"
      case PENDING     => "PENDING"

    }

  implicit val decodeStepStatus: Decoder[StepStatus] =
    Decoder.decodeString.emap {
      case "IN_PROGRESS" => Right(IN_PROGRESS)
      case "SUCCESS"     => Right(SUCCESS)
      case "ERROR"       => Right(ERROR)
      case "PENDING"     => Right(PENDING)

      case other => Left(s"Unknown StepStatus: $other")
    }
}
