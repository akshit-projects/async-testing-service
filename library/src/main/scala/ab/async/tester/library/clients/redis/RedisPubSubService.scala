package ab.async.tester.library.clients.redis

import io.circe.generic.auto._
import ab.async.tester.domain.execution.ExecutionStatusUpdate
import ab.async.tester.library.cache.{KafkaResourceCache, RedisClient}
import ab.async.tester.library.clients.events.KafkaClient
import akka.stream.scaladsl.SourceQueueWithComplete
import io.circe.parser
import io.circe.syntax.EncoderOps
import play.api.Configuration
import redis.clients.jedis.JedisPubSub

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}

class RedisPubSubService(redisClient: RedisClient, kafkaResourceCache: KafkaResourceCache, kafkaClient: KafkaClient, configuration: Configuration)(implicit ec: ExecutionContext) {
  private val pool = redisClient.getPool
  private val queues = new ConcurrentHashMap[String, SourceQueueWithComplete[io.circe.Json]]()

  private val sub = new JedisPubSub() {
    override def onMessage(channel: String, message: String): Unit = {
      parser.parse(message).flatMap(_.as[ExecutionStatusUpdate]).fold(
        err => println(s"Invalid update from redis: $err"),
        update => {
          val q = queues.get(update.executionId)
          if (q != null) q.offer(update.asJson)
        }
      )
    }
  }

  def startSubscription(channel: String)(implicit blockingEc: ExecutionContext): Unit = Future {
    val j = pool.getResource
    try j.subscribe(sub, channel)
    finally j.close()
  }(blockingEc)

  def registerQueue(executionId: String, queue: SourceQueueWithComplete[io.circe.Json]): Unit = queues.put(executionId, queue)
  def unregisterQueue(executionId: String): Unit = { val q = queues.remove(executionId); if (q != null) q.complete() }
  def close(): Unit = { sub.unsubscribe(); pool.close() }
}