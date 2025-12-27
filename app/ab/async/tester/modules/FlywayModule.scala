package ab.async.tester.modules

import com.google.inject.AbstractModule
import org.flywaydb.core.Flyway
import play.api.{Configuration, Environment, Logger}
import javax.inject.{Inject, Singleton}

class FlywayModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[FlywayRunner]).asEagerSingleton()
  }
}

@Singleton
class FlywayRunner @Inject() (
    configuration: Configuration,
    environment: Environment
) {
  private val logger = Logger(this.getClass)

  private val url = configuration.get[String]("flyway.url")
  private val user = configuration.get[String]("flyway.user")
  private val password = configuration.get[String]("flyway.password")
  private val locations = configuration.get[Seq[String]]("flyway.locations")
  private val baselineOnMigrate =
    configuration.get[Boolean]("flyway.baselineOnMigrate")
  private val validateOnMigrate =
    configuration.get[Boolean]("flyway.validateOnMigrate")

  logger.info("Running Flyway migration...")
  println("DEBUG: FlywayRunner is initializing...")

  val flyway = Flyway
    .configure()
    .dataSource(url, user, password)
    .locations(locations: _*)
    .baselineOnMigrate(baselineOnMigrate)
    .validateOnMigrate(validateOnMigrate)
    .load()

  try {
    flyway.migrate()
    logger.info("Flyway migration completed successfully.")
  } catch {
    case e: Exception =>
      logger.error("Flyway migration failed", e)
      throw e
  }
}
