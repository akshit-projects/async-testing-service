package ab.async.tester.workers.app.app

import ab.async.tester.workers.app.clients.kafka.KafkaConsumer
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.GraphDSL.Implicits.SourceArrow
import akka.stream.scaladsl.Sink

import scala.concurrent.ExecutionContextExecutor

object MainApp extends App {

  implicit val system: ActorSystem = ActorSystem("kafka-runner")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  KafkaConsumer.subscribeTopic("testTopic") ~> a ~> Sink.ignore
}
