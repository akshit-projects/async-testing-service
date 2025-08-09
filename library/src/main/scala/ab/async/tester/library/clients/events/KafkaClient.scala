package ab.async.tester.library.clients.events

import ab.async.tester.domain.clients.kafka.KafkaConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer

import java.util.Properties

class KafkaClient {

  def getKafkaPublisher(kafkaConfig: KafkaConfig): KafkaProducer[String, String] = {
    val props = new Properties()
    props.put("bootstrap.servers", kafkaConfig.bootstrapServers)
    props.put("security.protocol", kafkaConfig.securityProtocol)
    props.put("key.serializer", kafkaConfig.keySerializer)
    props.put("value.serializer", kafkaConfig.valueSerializer)

    // Add producer-specific configurations for reliability
    props.put("acks", "all") // Wait for all replicas to acknowledge
    props.put("retries", "3") // Retry failed sends
    props.put("batch.size", "16384") // Batch size in bytes
    props.put("linger.ms", "1") // Wait time for batching
    props.put("buffer.memory", "33554432") // Total memory for buffering
    props.put("enable.idempotence", "true") // Ensure exactly-once delivery

    // Add any additional config provided
    kafkaConfig.otherConfig.foreach {
      case (k, v) => props.put(k, v)
    }

    println(s"🔧 Creating Kafka producer with config:")
    props.forEach((k, v) => println(s"  $k = $v"))

    new KafkaProducer[String, String](props)
  }

}
