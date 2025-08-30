package ab.async.tester.workers.app

import ab.async.tester.workers.app.runner.{FlowRunner}
import com.google.inject.{Guice, Inject, Singleton}
import play.api.{Configuration, Logger}
import play.api.libs.ws.StandaloneWSClient

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
 * Main worker application that starts the flow execution consumer
 */
@Singleton
class WorkerApplication @Inject()(
                                   flowRunner: FlowRunner,
                                   configuration: Configuration,
                                   wsClient: StandaloneWSClient
                                 )(implicit ec: ExecutionContext) {

  private val logger = Logger(this.getClass)

  /**
   * Start the worker application
   */
  def start(): Unit = {
    logger.info("Starting Async Testing Service Worker...")
    logger.info(s"Configuration loaded: ${configuration.underlying.entrySet().size()} entries")

    Try {
      // Log configuration details
      val kafkaBootstrap = configuration.getOptional[String]("kafka.bootstrap.servers").getOrElse("NOT_SET")
      val workerTopic = configuration.getOptional[String]("events.workerQueueTopic").getOrElse("NOT_SET")
      val testSuiteTopic = configuration.getOptional[String]("events.testSuiteExecutionTopic").getOrElse("NOT_SET")
      val redisHost = configuration.getOptional[String]("redis.host").getOrElse("NOT_SET")

      logger.info(s"Kafka Bootstrap Servers: $kafkaBootstrap")
      logger.info(s"Worker Queue Topic: $workerTopic")
      logger.info(s"Test Suite Execution Topic: $testSuiteTopic")
      logger.info(s"Redis Host: $redisHost")

      // Start the flow consumer
      logger.info("Starting FlowRunner consumer...")
      flowRunner.startFlowConsumer()

      logger.info("Worker application started successfully")
      logger.info("Worker is now consuming Kafka messages and executing flows and test suites")

    } match {
      case Success(_) =>
        logger.info("All worker services started successfully")
      case Failure(exception) =>
        logger.error("Failed to start worker application", exception)
        exception.printStackTrace()
        System.exit(1)
    }
  }

  /**
   * Shutdown hook for graceful shutdown
   */
  def shutdown(): Unit = {
    logger.info("Shutting down worker application...")

    // Close WSClient to free up resources
    Try {
      wsClient.close()
      logger.info("WSClient closed successfully")
    }.recover {
      case e: Exception =>
        logger.error("Error closing WSClient", e)
    }

    logger.info("Worker application shutdown complete")
  }
}

/**
 * Main entry point for the worker application
 */
object WorkerMain extends App {
  // Initialize logging first
  System.setProperty("logback.configurationFile", "logback.xml")

  private val logger = Logger("WorkerMain")

  logger.info("Initializing Worker Application...")

  try {
    // Create Guice injector with worker modules
    val injector = Guice.createInjector(
      new WorkerModule()
    )

    // Get the worker application instance
    val workerApp = injector.getInstance(classOf[WorkerApplication])

    // Add shutdown hook
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      logger.info("Shutdown hook triggered")
      workerApp.shutdown()
    }))

    // Start the application
    workerApp.start()

    // Keep the application running
    logger.info("Worker application is running. Press Ctrl+C to stop.")

    // Block the main thread to keep the application alive
    val lock = new Object()
    lock.synchronized {
      lock.wait()
    }

  } catch {
    case e: Exception =>
      logger.error("Failed to start worker application", e)
      e.printStackTrace()
      System.exit(1)
  }
}
