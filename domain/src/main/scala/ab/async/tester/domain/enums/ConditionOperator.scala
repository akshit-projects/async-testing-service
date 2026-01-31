package ab.async.tester.domain.enums

import io.circe.{Decoder, Encoder}

sealed trait ConditionOperator {
  def stringified: String = this match {
    case ConditionOperator.Equals      => "=="
    case ConditionOperator.NotEquals   => "!="
    case ConditionOperator.Contains    => "contains"
    case ConditionOperator.GreaterThan => ">"
    case ConditionOperator.LessThan    => "<"
    case ConditionOperator.Exists      => "exists"
  }
}

object ConditionOperator {
  case object Equals extends ConditionOperator
  case object NotEquals extends ConditionOperator
  case object Contains extends ConditionOperator
  case object GreaterThan extends ConditionOperator
  case object LessThan extends ConditionOperator
  case object Exists extends ConditionOperator

  implicit val encoder: Encoder[ConditionOperator] =
    Encoder.encodeString.contramap[ConditionOperator] {
      case Equals      => "=="
      case NotEquals   => "!="
      case Contains    => "contains"
      case GreaterThan => ">"
      case LessThan    => "<"
      case Exists      => "exists"
    }

  implicit val decoder: Decoder[ConditionOperator] = Decoder.decodeString.emap {
    case "==" | "equals" | "eq"     => Right(Equals)
    case "!=" | "not_equals" | "ne" => Right(NotEquals)
    case "contains"                 => Right(Contains)
    case ">" | "gt"                 => Right(GreaterThan)
    case "<" | "lt"                 => Right(LessThan)
    case "exists"                   => Right(Exists)
    case other => Left(s"Unknown ConditionOperator: $other")
  }
}
