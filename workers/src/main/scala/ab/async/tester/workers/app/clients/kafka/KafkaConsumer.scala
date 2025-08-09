package ab.async.tester.workers.app.clients.kafka

import akka.actor.ActorSystem
import akka.kafka.{ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.ExecutionContext

object KafkaConsumer {

  private val kafkaConfig = ConfigFactory.load().getConfig("kafka")

  def subscribeTopic(topic: String)(
    implicit system: ActorSystem,
  ): Source[ConsumerMessage.CommittableMessage[String, String], Consumer.Control] = {

    val kafkaBrokers = kafkaConfig.getString("bootstrapServers")
    val groupId = kafkaConfig.getString("groupId")
    val consumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers(kafkaBrokers)
      .withGroupId(groupId)
      .withProperty("auto.offset.reset", "earliest")

    val committableSource = Consumer.committableSource(consumerSettings, Subscriptions.topics(topic))

    committableSource
  }
}
