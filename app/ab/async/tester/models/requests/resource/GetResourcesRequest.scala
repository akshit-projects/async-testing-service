package ab.async.tester.models.requests.resource

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

/**
 * Request model for filtering resources
 *
 * @param types List of resource types to filter by (empty means all types)
 * @param group Optional group to filter by
 * @param namespace Optional namespace to filter by
 */
case class GetResourcesRequest(
  types: Option[List[String]] = None,
  group: Option[String] = None,
  namespace: Option[String] = None
)

object GetResourcesRequest {
  implicit val encoder: Encoder[GetResourcesRequest] = deriveEncoder
  implicit val decoder: Decoder[GetResourcesRequest] = deriveDecoder
} 