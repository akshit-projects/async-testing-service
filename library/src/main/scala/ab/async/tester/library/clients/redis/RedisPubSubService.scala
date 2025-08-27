package ab.async.tester.library.clients.redis

import ab.async.tester.domain.execution.ExecutionStatusUpdate
import ab.async.tester.library.cache.RedisClient
import ab.async.tester.library.ec.RedisPubSubExecutorPool
import akka.stream.scaladsl.SourceQueueWithComplete
import com.google.inject.{Inject, Singleton}
import io.circe.generic.auto._
import io.circe.parser
import io.circe.syntax.EncoderOps
import play.api.Logger
import redis.clients.jedis.JedisPubSub

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RedisPubSubService @Inject()(redisClient: RedisClient)(implicit ec: ExecutionContext) {
  private val pool = redisClient.getPool
  private val logger = Logger(this.getClass)
  private val queues = new ConcurrentHashMap[String, mutable.Map[String, SourceQueueWithComplete[io.circe.Json]]]()

  startSubscription("internal-executions-topic")(RedisPubSubExecutorPool.getExecutionContext)

  private val sub = new JedisPubSub() {
    override def onMessage(channel: String, message: String): Unit = {
      parser.parse(message).flatMap(_.as[ExecutionStatusUpdate]).fold(
        err => println(s"Invalid update from redis: $err"),
        update => {
          val map = queues.get(update.executionId)
          if (map != null) {
            map.foreach {
              case (_, queue) =>
                if (queue != null) queue.offer(update.asJson)
            }
          }
        }
      )
    }
  }

  private def startSubscription(channel: String)(blockingEc: ExecutionContext): Unit = Future {
    val j = pool.getResource
    try {
      j.subscribe(sub, channel)
    } catch {
      case ex: Exception =>
        print(ex)
    }

  }(blockingEc)

  def registerQueue(executionId: String, clientId: Option[String], queue: SourceQueueWithComplete[io.circe.Json]): Unit = {
    synchronized {
      val map = if (queues.containsKey(executionId)) {
        queues.get(executionId)
      } else {
        new mutable.HashMap[String, SourceQueueWithComplete[io.circe.Json]]()
      }

      map.put(clientId.getOrElse(""), queue)
      queues.put(executionId, map)
    }

  }

  def unregisterQueue(executionId: String): Unit = {
    val map = queues.remove(executionId)
    if (map != null) {
      map.foreach {
        case (_, queue) => if (queue != null) queue.complete()
      }
    }
  }

  def close(): Unit = {
    sub.unsubscribe()
    pool.close()
  }
}