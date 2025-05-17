package ab.async.tester.service.resource

import ab.async.tester.models.resource.ResourceConfig
import ab.async.tester.models.requests.resource.GetResourcesRequest
import ab.async.tester.repository.resource.ResourceRepository
import ab.async.tester.metrics.MetricConstants
import ab.async.tester.utils.MetricUtils
import com.google.inject.Inject
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import javax.inject.Singleton

@Singleton
class ResourceServiceImpl @Inject()(
  resourceRepository: ResourceRepository
)(implicit ec: ExecutionContext) extends ResourceServiceTrait {

  private implicit val logger: Logger = Logger(this.getClass)
  private val dbMetrics = MetricConstants.DB_OPERATIONS
  private val dbLatency = MetricConstants.DB_LATENCY
  private val serviceName = "ResourceService"
  
  /**
   * Get all resources, optionally filtered by request parameters
   *
   * @param request the filter criteria for resources
   * @return list of resource configs
   */
  override def getResources(request: GetResourcesRequest): Future[List[ResourceConfig]] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "getResources") {
        resourceRepository.findAll(request).recover {
          case e: Exception =>
            logger.error(s"Error retrieving resources: ${e.getMessage}", e)
            List.empty
        }
      }
  }
  
  /**
   * Get a resource by ID
   *
   * @param id the resource ID
   * @return the resource if found
   */
  override def getResourceById(id: String): Future[Option[ResourceConfig]] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "getResourceByID") {
      resourceRepository.findById(id).recover {
        case e: Exception =>
          logger.error(s"Error retrieving resource $id: ${e.getMessage}", e)
          None
      }
    }
  }
  
  /**
   * Create a new resource
   *
   * @param resourceConfig the resource to create
   * @return the created resource with ID
   */
  override def createResource(resourceConfig: ResourceConfig): Future[ResourceConfig] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "createResource") {
      resourceRepository.create(resourceConfig).recover {
        case e: Exception =>
          logger.error(s"Error creating resource: ${e.getMessage}", e)
          throw e
      }
    }
  }
  
  /**
   * Update an existing resource
   *
   * @param resourceConfig the resource to update
   * @return the updated resource if successful
   */
  override def updateResource(resourceConfig: ResourceConfig): Future[Option[ResourceConfig]] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "updateResource") {
      resourceRepository.update(resourceConfig).recover {
        case e: Exception =>
          dbMetrics.labels("update", "resources", "error").inc()
          logger.error(s"Error updating resource ${resourceConfig.getId}: ${e.getMessage}", e)
          None
      }
    }
  }
  
  /**
   * Delete a resource by ID
   *
   * @param id the resource ID to delete
   * @return true if deleted, false otherwise
   */
  override def deleteResource(id: String): Future[Boolean] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "deleteResource") {
      resourceRepository.delete(id).recover {
        case e: Exception =>
          dbMetrics.labels("delete", "resources", "error").inc()
          logger.error(s"Error deleting resource $id: ${e.getMessage}", e)
          false
      }
    }
  }
} 