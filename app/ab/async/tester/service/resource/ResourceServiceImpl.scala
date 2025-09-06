package ab.async.tester.service.resource

import ab.async.tester.domain.common.{PaginatedResponse, PaginationMetadata}
import ab.async.tester.domain.resource.ResourceConfig
import ab.async.tester.library.metrics.MetricConstants
import ab.async.tester.library.repository.resource.ResourceRepository
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.Inject
import play.api.Logger

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ResourceServiceImpl @Inject()(
  resourceRepository: ResourceRepository
)(implicit ec: ExecutionContext) extends ResourceService {

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
  override def getResources(typesOpt: Option[List[String]], groupOpt: Option[String], namespaceOpt: Option[String], limit: Int, page: Int): Future[PaginatedResponse[ResourceConfig]] = {
    MetricUtils.withAsyncServiceMetrics(serviceName, "getResources") {
      resourceRepository.findAllWithCount(typesOpt, groupOpt, namespaceOpt, limit, page).map { case (resources, total) =>
        PaginatedResponse(
          data = resources,
          pagination = PaginationMetadata(page, limit, total)
        )
      }.recover {
        case e: Exception =>
          logger.error(s"Error retrieving resources with pagination: ${e.getMessage}", e)
          PaginatedResponse(List.empty, PaginationMetadata(page, limit, 0))
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