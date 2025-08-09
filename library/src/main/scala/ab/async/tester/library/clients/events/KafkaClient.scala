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

    // Add any additional config provided
    kafkaConfig.otherConfig.map {
      case (k, v) => props.put(k, v)
    }

    new KafkaProducer[String, String](props)
  }

}
