package ab.async.tester.cache

import javax.inject._
import play.api.inject.{SimpleModule, _}
import play.api.{Configuration, Environment, Logger}
import play.api.ApplicationLoader.Context

import scala.concurrent.{ExecutionContext, Future}
import play.api.inject.ApplicationLifecycle
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
  kafkaResourceCache: KafkaResourceCache
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
    }
  }
} 