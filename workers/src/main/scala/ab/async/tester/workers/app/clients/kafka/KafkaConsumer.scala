package ab.async.tester.workers.app.clients.kafka

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory
import org.apache.kafka.common.serialization.StringDeserializer
object KafkaConsumer {

  private val kafkaConfig = ConfigFactory.load().getConfig("kafka")

  def subscribeTopic(topic: String)(
    implicit system: ActorSystem
  ): Source[ConsumerMessage.CommittableMessage[String, String], Consumer.Control] = {

    val kafkaBrokers = kafkaConfig.getString("bootstrapServers")
    val groupId = kafkaConfig.getString("groupId")
    val securityProtocol = kafkaConfig.getConfig("security").getString("protocol")

    val baseSettings =
      ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
        .withBootstrapServers(kafkaBrokers)
        .withGroupId(groupId)
        .withProperty("auto.offset.reset", "earliest")
        .withProperty("security.protocol", securityProtocol)

    val sslSettings =
      if (securityProtocol == "SSL" || securityProtocol == "SASL_SSL") {
        val sslConfig = kafkaConfig.getConfig("ssl")

        sslConfig.entrySet().toArray.foldLeft(baseSettings) {
          case (settings, entry: java.util.Map.Entry[String, _]) =>
            val key = entry.getKey.replace(".", "_") match {
              case "truststore_location" => "ssl.truststore.location"
              case "truststore_password" => "ssl.truststore.password"
              case "keystore_location" => "ssl.keystore.location"
              case "keystore_password" => "ssl.keystore.password"
              case "keystore_keyPassword" => "ssl.key.password"
              case other => other
            }

            settings.withProperty(key, sslConfig.getString(entry.getKey))
        }
      } else baseSettings

    Consumer.committableSource(
      sslSettings,
      Subscriptions.topics(topic)
    )
  }
}

