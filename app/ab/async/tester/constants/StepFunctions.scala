package ab.async.tester.constants

object StepFunctions {
  val HTTP_API_STEP = "http"
  val DELAY_STEP = "delay"
  val PUBLISH_KAFKA_MESSAGE_STEP = "publish-kafka-message"
  val SUBSCRIBE_KAFKA_MESSAGES_STEP = "subscribe-kafka-topic"

  // Combined set of all supported step function types
  val ALL_STEP_FUNCTIONS: Set[String] = Set(
    HTTP_API_STEP,
    DELAY_STEP,
    PUBLISH_KAFKA_MESSAGE_STEP,
    SUBSCRIBE_KAFKA_MESSAGES_STEP
  ).map(_.toLowerCase)
}
