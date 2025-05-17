package ab.async.tester.db

import com.google.inject.{AbstractModule, Provides, Singleton}
import javax.inject.Named
import org.mongodb.scala._
import play.api.{Configuration, Logger}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
 * Module for MongoDB configuration and collection provision
 */
class MongoDBModule extends AbstractModule {
  private val logger = Logger(this.getClass)

  override def configure(): Unit = {
  }

  @Provides
  @Singleton
  def mongoClient(configuration: Configuration): MongoClient = {
    logger.info("Creating MongoDB client")
    
    val connectionString = configuration.get[String]("mongodb.uri")
    val settings = MongoClientSettings.builder()
      .applyConnectionString(ConnectionString(connectionString))
      .build()
    
    val client = MongoClient(settings)
    
    // Test connection to log success/failure
    try {
      // Simple ping to test connection
      val result = Await.result(client.getDatabase("admin").runCommand(Document("ping" -> 1)).toFuture(), 5.seconds)
      logger.info(s"Successfully connected to MongoDB at $connectionString")
    } catch {
      case e: Exception =>
        logger.error(s"Failed to connect to MongoDB at $connectionString: ${e.getMessage}", e)
    }
    
    client
  }

  @Provides
  @Singleton
  def mongoDatabase(
    client: MongoClient,
    configuration: Configuration
  ): MongoDatabase = {
    logger.info("Creating MongoDB database")
    
    val dbName = configuration.get[String]("mongodb.database")
    val database = client.getDatabase(dbName)
    
    logger.info(s"Using database: $dbName")
    database
  }

  @Provides
  @Singleton
  @Named("flowCollection")
  def flowCollection(
    database: MongoDatabase
  ): MongoCollection[Document] = {
    logger.info("Creating flows collection")
    
    val collection = database.getCollection("asynctester")
    logger.info("Connected to flows collection")
    
    collection
  }
  
  @Provides
  @Singleton
  @Named("resourceCollection")
  def resourceCollection(client: MongoClient): MongoCollection[Document] = {
    logger.info("Creating resources collection")
    val db = client.getDatabase("asynctester")
    db.getCollection("resources")
  }
  
  @Provides
  @Singleton
  @Named("executionCollection")
  def executionCollection(client: MongoClient): MongoCollection[Document] = {
    logger.info("Creating executions collection")
    val db = client.getDatabase("asynctester")
    db.getCollection("executions")
  }
} 