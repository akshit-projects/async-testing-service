package ab.async.tester.repository.resource

import ab.async.tester.converters.ResourceConverters
import ab.async.tester.models.requests.resource.GetResourcesRequest
import ab.async.tester.models.resource._
import ab.async.tester.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Provides, Singleton}
import org.mongodb.scala._
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.FindOneAndReplaceOptions
import play.api.Logger

import javax.inject.Named
import scala.concurrent.{ExecutionContext, Future}

/**
 * Repository for resource configuration persistence
 */
@ImplementedBy(classOf[ResourceRepositoryImpl])
trait ResourceRepository {
  def findById(id: String): Future[Option[ResourceConfig]]
  def findAll(request: GetResourcesRequest): Future[List[ResourceConfig]]
  def create(resource: ResourceConfig): Future[ResourceConfig]
  def update(resource: ResourceConfig): Future[Option[ResourceConfig]]
  def delete(id: String): Future[Boolean]
  def findByUniqueFields(resource: ResourceConfig): Future[Option[ResourceConfig]]
}

@Singleton
@Provides
class ResourceRepositoryImpl @Inject()(
  @Named("resourceCollection") collection: MongoCollection[Document]
)(implicit ec: ExecutionContext) extends ResourceRepository {
  private implicit val logger: Logger = Logger(this.getClass)
  private val repositoryName = "ResourceRepository"

  /**
   * Finds resource by ID
   */
  override def findById(id: String): Future[Option[ResourceConfig]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findById") {
      try {
        collection.find(equal("_id", id))
          .first()
          .toFutureOption()
          .map(docOpt => docOpt.flatMap(ResourceConverters.fromDocument))
          .recover {
            case e: Exception =>
              logger.error(s"Error finding resource by ID $id: ${e.getMessage}", e)
              None
          }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing findById for resource $id: ${e.getMessage}", e)
          Future.successful(None)
      }
    }
  }

  /**
   * Finds all resources, optionally filtered by the request parameters
   */
  override def findAll(request: GetResourcesRequest): Future[List[ResourceConfig]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findAll") {
      try {
        val filters = scala.collection.mutable.ListBuffer[Bson]()

        // Add type filter if specified
        request.types.foreach { types =>
          if (types.nonEmpty) {
            filters += in("type", types:_*)
          }
        }

        // Add group filter if specified
        request.group.foreach { group =>
          filters += equal("group", group)
        }

        // Add namespace filter if specified
        request.namespace.foreach { namespace =>
          filters += equal("namespace", namespace)
        }

        // Combine all filters
        val filter = if (filters.isEmpty) {
          empty()
        } else if (filters.size == 1) {
          filters.head
        } else {
          and(filters.toSeq:_*)
        }

        collection.find(filter)
          .collect()
          .toFuture()
          .map(docs => docs.flatMap(ResourceConverters.fromDocument).toList)
          .recover {
            case e: Exception =>
              logger.error(s"Error finding resources: ${e.getMessage}", e)
              List.empty
          }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing findAll for resources: ${e.getMessage}", e)
          Future.successful(List.empty)
      }
    }
  }

  /**
   * Finds a resource by its unique fields based on its type
   */
  override def findByUniqueFields(resource: ResourceConfig): Future[Option[ResourceConfig]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findByUniqueFields") {
      try {
        val filter = resource match {
          case db: SQLDBConfig =>
            and(
              equal("type", "database"),
              equal("dbUrl", db.dbUrl),
              equal("username", db.username),
              equal("password", db.password)
            )
          case kafka: KafkaConfig =>
            and(
              equal("type", "kafka"),
              equal("brokerList", kafka.brokerList)
            )
          case http: APISchemaConfig =>
            and(
              equal("type", "api-schema"),
              equal("url", http.url),
              equal("method", http.method)
            )
          case _ =>
            and()
        }

        collection.find(filter)
          .first()
          .toFutureOption()
          .map(docOpt => docOpt.flatMap(ResourceConverters.fromDocument))
          .recover {
            case e: Exception =>
              logger.error(s"Error finding resource by unique fields: ${e.getMessage}", e)
              None
          }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing findByUniqueFields: ${e.getMessage}", e)
          Future.successful(None)
      }
    }
  }

  /**
   * Creates a new resource, but only if it doesn't already exist
   */
  override def create(resource: ResourceConfig): Future[ResourceConfig] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "create") {
      try {
        // First check if resource with similar unique fields exists
        findByUniqueFields(resource).flatMap {
          case Some(existingResource) =>
            logger.info(s"Resource of type ${resource.getType} with similar properties already exists with id: ${existingResource.getId}")
            Future.successful(existingResource)

          case None =>
            // Resource doesn't exist, proceed with creation
            val document = ResourceConverters.toDocument(resource)

            collection.insertOne(document)
              .toFuture()
              .map(result => {
                val insertedId = result.getInsertedId.asString().getValue
                resource.setId(insertedId)
                resource
              })
              .recover {
                case e: Exception =>
                  logger.error(s"Error creating resource: ${e.getMessage}", e)
                  throw e
              }
        }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing create for resource: ${e.getMessage}", e)
          Future.failed(e)
      }
    }
  }

  /**
   * Updates an existing resource
   */
  override def update(resource: ResourceConfig): Future[Option[ResourceConfig]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "update") {
      try {
        val document = ResourceConverters.toDocument(resource)
        val id = resource.getId
        val filter = and(equal("_id", id), equal("type", resource.getType))

        collection.findOneAndReplace(
            filter,
            document,
            FindOneAndReplaceOptions().upsert(false)
          )
          .toFutureOption()
          .map { oldDoc =>
            if (oldDoc.isDefined) Some(resource) else None
          }
          .recover {
            case e: Exception =>
              logger.error(s"Error updating resource $id: ${e.getMessage}", e)
              None
          }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing update for resource: ${e.getMessage}", e)
          Future.successful(None)
      }
    }
  }

  /**
   * Deletes a resource by ID
   */
  override def delete(id: String): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "delete") {
      try {
        collection.deleteOne(equal("_id", id))
          .toFuture()
          .map(result => result.getDeletedCount > 0)
          .recover {
            case e: Exception =>
              logger.error(s"Error deleting resource $id: ${e.getMessage}", e)
              false
          }
      } catch {
        case e: Exception =>
          logger.error(s"Error processing delete for resource $id: ${e.getMessage}", e)
          Future.successful(false)
      }
    }
  }
}