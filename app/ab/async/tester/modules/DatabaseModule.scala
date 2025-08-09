package ab.async.tester.modules

import com.google.inject.{AbstractModule, Provides, Singleton}
import play.api.Configuration
import slick.jdbc.PostgresProfile.api._
import javax.inject.Named

class DatabaseModule extends AbstractModule {

  @Provides
  @Singleton
  def provideDatabase(configuration: Configuration): Database = {
    // Get database configuration
    val url = configuration.getOptional[String]("slick.dbs.default.db.url")
      .getOrElse("jdbc:postgresql://localhost:5432/asynctester")
    val user = configuration.getOptional[String]("slick.dbs.default.db.user")
      .getOrElse("asynctester")
    val password = configuration.getOptional[String]("slick.dbs.default.db.password")
      .getOrElse("asynctester")
    val driver = configuration.getOptional[String]("slick.dbs.default.db.driver")
      .getOrElse("org.postgresql.Driver")

    Database.forURL(
      url = url,
      user = user,
      password = password,
      driver = driver
    )
  }
}
