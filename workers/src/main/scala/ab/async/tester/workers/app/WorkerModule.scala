package ab.async.tester.workers.app

import ab.async.tester.library.repository.execution.{
  ExecutionRepository,
  ExecutionRepositoryImpl
}
import ab.async.tester.library.repository.resource.{
  ResourceRepository,
  ResourceRepositoryImpl
}
import ab.async.tester.workers.app.runner._
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.typesafe.config.ConfigFactory
import play.api.Configuration
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext

/** Guice module for worker dependencies
  */
class WorkerModule extends AbstractModule {

  override def configure(): Unit = {
    // Bind step runners
    bind(classOf[HttpStepRunner]).asEagerSingleton()
    bind(classOf[DelayStepRunner]).asEagerSingleton()
    bind(classOf[KafkaPublisherStepRunner]).asEagerSingleton()
    bind(classOf[KafkaConsumerStepRunner]).asEagerSingleton()
    
    // Bind registry
    bind(classOf[StepRunnerRegistry]).to(classOf[StepRunnerRegistryImpl]).asEagerSingleton()
    
    // Bind flow runner
    bind(classOf[FlowRunner]).to(classOf[FlowRunnerImpl]).asEagerSingleton()
  }

  @Provides
  @Singleton
  def provideConfiguration(): Configuration = {
    val config = ConfigFactory.load()
    Configuration(config)
  }

  @Provides
  @Singleton
  def provideActorSystem(): ActorSystem = {
    ActorSystem("worker-system")
  }

  @Provides
  @Singleton
  def provideExecutionContext(actorSystem: ActorSystem): ExecutionContext = {
    actorSystem.dispatcher
  }

  @Provides
  @Singleton
  def provideMaterializer(actorSystem: ActorSystem): Materializer = {
    Materializer(actorSystem)
  }

  @Provides
  @Singleton
  def provideDatabase(configuration: Configuration): Database = {
    // Get database configuration
    val url = configuration
      .getOptional[String]("slick.dbs.default.db.url")
      .getOrElse("jdbc:postgresql://localhost:5432/asynctester")
    val user = configuration
      .getOptional[String]("slick.dbs.default.db.user")
      .getOrElse("asynctester")
    val password = configuration
      .getOptional[String]("slick.dbs.default.db.password")
      .getOrElse("asynctester")
    val driver = configuration
      .getOptional[String]("slick.dbs.default.db.driver")
      .getOrElse("org.postgresql.Driver")

    Database.forURL(
      url = url,
      user = user,
      password = password,
      driver = driver
    )
  }

  @Provides
  @Singleton
  def provideExecutionRepository(
      database: Database
  )(implicit ec: ExecutionContext): ExecutionRepository = {
    new ExecutionRepositoryImpl(database)
  }

  @Provides
  @Singleton
  def provideResourceRepository(
      database: Database
  )(implicit ec: ExecutionContext): ResourceRepository = {
    new ResourceRepositoryImpl(database)
  }

  @Provides
  @Singleton
  def provideWSClient()(implicit
      actorSystem: ActorSystem,
      materializer: Materializer
  ): StandaloneWSClient = {
    StandaloneAhcWSClient()
  }
}
