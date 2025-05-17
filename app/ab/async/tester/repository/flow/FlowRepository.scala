package ab.async.tester.repository.flow

import ab.async.tester.converters.FlowConverters
import ab.async.tester.models.flow.Floww
import ab.async.tester.models.requests.flow.GetFlowsRequest
import ab.async.tester.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Singleton}
import org.mongodb.scala._
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.ReplaceOptions
import play.api.Logger

import javax.inject.Named
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/**
 * Repository for flow persistence in MongoDB
 */
@ImplementedBy(classOf[FlowRepositoryImpl])
trait FlowRepository {
  def findById(id: String): Future[Option[Floww]]
  def findAll(request: GetFlowsRequest): Future[List[Floww]]
  def insert(flow: Floww): Future[Floww]
  def update(flow: Floww): Future[Boolean]
  def findByName(name: String): Future[Option[Floww]]
}

@Singleton
class FlowRepositoryImpl @Inject()(
  @Named("flowCollection") collection: MongoCollection[Document]
)(implicit ec: ExecutionContext) extends FlowRepository {
  private implicit val logger: Logger = Logger(this.getClass)
  private val repositoryName = "FlowRepository"
  
  /**
   * Finds a flow by its ID
   *
   * @param id the flow ID
   * @return the flow if found
   */
  override def findById(id: String): Future[Option[Floww]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findById") {
      try {
        collection.find(equal("_id", id))
          .first()
          .toFutureOption()
          .map(docOpt => docOpt.flatMap(FlowConverters.documentToFlow))
          .recover {
            case e: Exception =>
              logger.error(s"Error finding flow by ID $id: ${e.getMessage}", e)
              None
          }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing findById for ID $id: ${e.getMessage}", e)
          Future.successful(None)
      }
    }
  }
  
  /**
   * Finds a flow by its name
   * 
   * @param name the flow name
   * @return the flow if found
   */
  override def findByName(name: String): Future[Option[Floww]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findByName") {
      try {
        collection.find(equal("name", name))
          .first()
          .toFutureOption()
          .map(docOpt => docOpt.flatMap(FlowConverters.documentToFlow))
          .recover {
            case e: Exception =>
              logger.error(s"Error finding flow by name $name: ${e.getMessage}", e)
              None
          }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing findByName for name $name: ${e.getMessage}", e)
          Future.successful(None)
      }
    }
  }
  
  /**
   * Finds flows based on filter criteria
   *
   * @param request the filter criteria
   * @return list of matching flows
   */
  override def findAll(request: GetFlowsRequest): Future[List[Floww]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findAll") {
      try {
        // Build filter
        val filter = {
          val searchFilter = request.search match {
            case Some(search) if search.nonEmpty =>
              regex("name", s".*$search.*", "i")
            case _ => empty()
          }
          
          val idsFilter = request.ids match {
            case Some(ids) if ids.nonEmpty =>
              in("_id", ids.asJava)
            case _ => empty()
          }
          
          // Combine filters
          if (searchFilter != empty() && idsFilter != empty()) {
            and(searchFilter, idsFilter)
          } else if (searchFilter != empty()) {
            searchFilter
          } else if (idsFilter != empty()) {
            idsFilter
          } else {
            empty()
          }
        }
        
        collection.find(filter)
          .limit(request.limit)
          .collect()
          .toFuture()
          .map(docs => docs.flatMap(FlowConverters.documentToFlow).toList)
          .recover {
            case e: Exception =>
              logger.error(s"Error finding flows: ${e.getMessage}", e)
              List.empty[Floww]
          }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing findAll: ${e.getMessage}", e)
          Future.successful(List.empty[Floww])
      }
    }
  }
  
  /**
   * Inserts a new flow, but only if a flow with the same name doesn't already exist
   *
   * @param flow the flow to insert
   * @return the inserted flow ID
   */
  override def insert(flow: Floww): Future[Floww] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "insert") {
      try {
        val flowId = flow.id.getOrElse(new ObjectId().toString)
        val flowWithId = flow.copy(id = Some(flowId))

        val document = FlowConverters.flowToDocument(flowWithId)

        collection.insertOne(document)
          .toFuture()
          .map(_ => flowWithId)
          .recover {
            case e: Exception =>
              logger.error(s"Error inserting flow: ${e.getMessage}", e)
              throw e
          }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing insert: ${e.getMessage}", e)
          Future.failed(e)
      }
    }
  }
  
  /**
   * Updates an existing flow
   *
   * @param flow the flow to update
   * @return true if the flow was updated
   */
  override def update(flow: Floww): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "update") {
      flow.id match {
        case Some(id) =>
          try {
            val document = FlowConverters.flowToDocument(flow)
            val options = new ReplaceOptions().upsert(false)
            
            collection.replaceOne(equal("_id", id), document, options)
              .toFuture()
              .map(result => result.getModifiedCount > 0)
              .recover {
                case e: Exception =>
                  logger.error(s"Error updating flow $id: ${e.getMessage}", e)
                  false
              }
          } catch {
            case e: Exception =>
              logger.error(s"Error processing update for flow $id: ${e.getMessage}", e)
              Future.successful(false)
          }
          
        case None =>
          logger.error("Cannot update flow without ID")
          Future.successful(false)
      }
    }
  }
} 