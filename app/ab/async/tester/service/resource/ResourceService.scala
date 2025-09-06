package ab.async.tester.service.resource

import ab.async.tester.domain.common.PaginatedResponse
import ab.async.tester.domain.resource.ResourceConfig
import com.google.inject.ImplementedBy

import scala.concurrent.Future

@ImplementedBy(classOf[ResourceServiceImpl])
trait ResourceService {
  /**
   * Get all resources with pagination, optionally filtered by request parameters
   *
   * @param typesOpt optional list of resource types to filter by
   * @param groupOpt optional group to filter by
   * @param namespaceOpt optional namespace to filter by
   * @param limit number of items per page
   * @param page page number (0-based)
   * @return paginated response with resources and metadata
   */
  def getResources(typesOpt: Option[List[String]], groupOpt: Option[String], namespaceOpt: Option[String], limit: Int, page: Int): Future[PaginatedResponse[ResourceConfig]]
  
  /**
   * Get a resource by ID
   *
   * @param id the resource ID
   * @return the resource if found
   */
  def getResourceById(id: String): Future[Option[ResourceConfig]]

  /**
   * Create a new resource
   *
   * @param resourceConfig the resource to create
   * @return the created resource with ID
   */
  def createResource(resourceConfig: ResourceConfig): Future[ResourceConfig]
  
  /**
   * Update an existing resource
   *
   * @param resourceConfig the resource to update
   * @return the updated resource if successful
   */
  def updateResource(resourceConfig: ResourceConfig): Future[Option[ResourceConfig]]
  
  /**
   * Delete a resource by ID
   *
   * @param id the resource ID to delete
   * @return true if deleted, false otherwise
   */
  def deleteResource(id: String): Future[Boolean]
}
