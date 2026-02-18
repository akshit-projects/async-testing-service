package ab.async.tester.domain.step

import ab.async.tester.domain.enums.StepType
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}

trait StepMeta {
  def stepType: StepType
}


object StepMeta {

  implicit val encodeStepMeta: Encoder[StepMeta] = Encoder.instance { meta =>
    StepTypeRegistry
      .getMetadata(meta.stepType)
      .encoder
      .asInstanceOf[Encoder[StepMeta]]
      .apply(meta)
  }

  implicit val decodeStepMeta: Decoder[StepMeta] = (c: HCursor) => {

    val fields = c.keys.map(_.toSet).getOrElse(Set.empty)

    StepTypeRegistry
      .fromDiscriminatorFields(fields)
      .toRight(DecodingFailure("Unknown step meta", c.history))
      .flatMap { stepType =>
        StepTypeRegistry
          .getMetadata(stepType)
          .decoder
          .asInstanceOf[Decoder[StepMeta]]
          .tryDecode(c)
      }
  }
}
