package ab.async.tester.domain.clients.kafka

case class KafkaConfig(
  bootstrapServers: String,
  securityProtocol: String = "PLAINTEXT",
  keySerializer: String = "org.apache.kafka.common.serialization.StringSerializer",
  valueSerializer: String = "org.apache.kafka.common.serialization.StringSerializer",
  otherConfig: Map[String, String] = Map.empty
)