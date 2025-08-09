package ab.async.tester

import ab.async.tester.library.cache.KafkaResourceCache
import ab.async.tester.library.clients.redis.RedisPubSubService
import play.api.Logger
import play.api.inject._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Module that registers the application lifecycle hook
 */
class AppLifecycleModule extends SimpleModule(bind[AppLifecycleHook].toSelf.eagerly())

/**
 * Lifecycle hook to manage resources when the application stops
 */
@Singleton
class AppLifecycleHook @Inject()(
  lifecycle: ApplicationLifecycle, 
  kafkaResourceCache: KafkaResourceCache,
  redisPubSubService: RedisPubSubService
)(implicit ec: ExecutionContext) {
  
  private val logger = Logger(this.getClass)
  
  logger.info("Registering application shutdown hook")
  
  lifecycle.addStopHook { () =>
    Future {
      logger.info("Application shutting down, cleaning up resources...")
      
      Try(kafkaResourceCache.shutdownAll()) match {
        case Success(_) => logger.info("Successfully closed Kafka resources")
        case Failure(e) => logger.error(s"Error closing Kafka resources: ${e.getMessage}", e)
      }

      Try(redisPubSubService.close())
    }
  }
} 