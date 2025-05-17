package ab.async.tester.repository.execution

import ab.async.tester.converters.ExecutionConverters
import ab.async.tester.models.execution.{Execution, ExecutionStatus}
import ab.async.tester.models.flow.Floww
import ab.async.tester.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Singleton}
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.UpdateOptions
import play.api.Logger

import javax.inject.Named
import scala.concurrent.{ExecutionContext, Future}

/**
 * Repository for flow execution persistence
 */
@ImplementedBy(classOf[ExecutionRepositoryImpl])
trait ExecutionRepository {
  def saveExecution(flow: Floww): Future[Execution]
  def saveExecution(execution: Execution): Future[Execution]
  def findById(id: String): Future[Option[Execution]]
  def updateStatus(id: String, status: ExecutionStatus): Future[Boolean]
}

@Singleton
class ExecutionRepositoryImpl @Inject()(
  @Named("executionCollection") collection: MongoCollection[Document]
)(implicit ec: ExecutionContext) extends ExecutionRepository {
  private implicit val logger: Logger = Logger(this.getClass)
  private val repositoryName = "ExecutionRepository"
  
  /**
   * Saves a new execution for a flow
   */
  override def saveExecution(flow: Floww): Future[Execution] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "saveExecution") {
      try {
        val execution = Execution.fromFlow(flow)
        val document = ExecutionConverters.toDocument(execution)
        
        collection.insertOne(document)
          .toFuture()
          .map { result =>
            val insertedId = result.getInsertedId.asString().getValue
            execution.copy(id = Some(insertedId))
          }
          .recover {
            case e: Exception =>
              logger.error(s"Error saving execution: ${e.getMessage}", e)
              throw e
          }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing saveExecution: ${e.getMessage}", e)
          Future.failed(e)
      }
    }
  }
  
  /**
   * Saves an existing execution
   */
  override def saveExecution(execution: Execution): Future[Execution] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "saveExecution") {
      try {
        val document = ExecutionConverters.toDocument(execution)
        
        collection.insertOne(document)
          .toFuture()
          .map { result =>
            val insertedId = result.getInsertedId.asString().getValue
            execution.copy(id = Some(insertedId))
          }
          .recover {
            case e: Exception =>
              logger.error(s"Error saving execution: ${e.getMessage}", e)
              throw e
          }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing saveExecution: ${e.getMessage}", e)
          Future.failed(e)
      }
    }
  }
  
  /**
   * Finds execution by ID
   */
  override def findById(id: String): Future[Option[Execution]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findById") {
      try {
        collection.find(equal("_id", id))
          .first()
          .toFutureOption()
          .map(docOpt => docOpt.flatMap(ExecutionConverters.fromDocument))
          .recover {
            case e: Exception =>
              logger.error(s"Error finding execution by ID $id: ${e.getMessage}", e)
              None
          }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing findById for execution $id: ${e.getMessage}", e)
          Future.successful(None)
      }
    }
  }
  
  /**
   * Updates execution status
   */
  override def updateStatus(id: String, status: ExecutionStatus): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updateStatus") {
      try {
        val update = Document(
          "$set" -> Document(
            "status" -> status.toString,
            "modifiedAt" -> (System.currentTimeMillis() / 1000)
          )
        )
        
        collection.updateOne(equal("_id", id), update, UpdateOptions().upsert(false))
          .toFuture()
          .map(result => result.getModifiedCount > 0)
          .recover {
            case e: Exception =>
              logger.error(s"Error updating execution status $id: ${e.getMessage}", e)
              false
          }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing updateStatus for execution $id: ${e.getMessage}", e)
          Future.successful(false)
      }
    }
  }
} 