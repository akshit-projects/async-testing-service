package ab.async.tester.models.requests.flow

/**
 * Request model for fetching flows with filtering options
 *
 * @param search optional search term to filter flows by name
 * @param limit maximum number of flows to return
 * @param page pagination parameter
 * @param ids optional list of flow IDs to fetch specific flows
 */
case class GetFlowsRequest(
  search: Option[String] = None,
  limit: Int = 10,
  page: Int = 0,
  ids: Option[List[String]] = None
)
