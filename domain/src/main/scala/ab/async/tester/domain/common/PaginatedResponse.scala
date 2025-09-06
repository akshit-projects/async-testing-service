package ab.async.tester.domain.common

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
 * Standardized paginated response structure for all list APIs
 */
case class PaginatedResponse[T](
  data: List[T],
  pagination: PaginationMetadata
)

case class PaginationMetadata(
  page: Int,
  limit: Int,
  total: Long,
  totalPages: Int,
  hasNext: Boolean,
  hasPrevious: Boolean
)

object PaginationMetadata {
  def apply(page: Int, limit: Int, total: Long): PaginationMetadata = {
    val totalPages = if (total == 0) 0 else Math.ceil(total.toDouble / limit).toInt
    val hasNext = page < totalPages - 1
    val hasPrevious = page > 0
    
    PaginationMetadata(
      page = page,
      limit = limit,
      total = total,
      totalPages = totalPages,
      hasNext = hasNext,
      hasPrevious = hasPrevious
    )
  }
  
  implicit val paginationMetadataEncoder: Encoder[PaginationMetadata] = deriveEncoder
  implicit val paginationMetadataDecoder: Decoder[PaginationMetadata] = deriveDecoder
}

object PaginatedResponse {
  implicit def paginatedResponseEncoder[T: Encoder]: Encoder[PaginatedResponse[T]] = deriveEncoder
  implicit def paginatedResponseDecoder[T: Decoder]: Decoder[PaginatedResponse[T]] = deriveDecoder
}
