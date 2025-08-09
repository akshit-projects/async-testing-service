package ab.async.tester.workers.app.runner

import scala.concurrent.{ExecutionContext, Future}


/**
 * HTTP step runner for making HTTP requests
 */
@Singleton
class HttpStepRunner @Inject()(wsClient: WSClient)(implicit ec: ExecutionContext, resourceRepository: ResourceRepository) extends BaseStepRunner {
  override protected val runnerName: String = "HttpStepRunner"

  override protected def executeStep(step: FlowStep, previousResults: List[StepResponse]): Future[StepResponse] = {
    try {
      // Extract step metadata - get the HttpStepMeta if available or create a default
      val httpMeta = step.meta match {
        case meta: HttpStepMeta => meta
        case _ =>
          logger.error(s"Invalid input for step: ${step.name} for step: $step")
          throw new Exception(s"Invalid input for step: ${step.name}")
      }


      resourceRepository.findById(httpMeta.resourceId).flatMap { resource =>
        val apiConfig = resource match {
          case Some(aPISchemaConfig: APISchemaConfig) => aPISchemaConfig
          case None =>
            throw new IllegalStateException(s"The resource for step: ${step.name} is not found")
          case _ =>
            throw new IllegalStateException(s"The resourceId is not valid for step: ${step.name}")
        }
        if (apiConfig.url.isEmpty) {
          Future.failed(new IllegalArgumentException("URL is required for HTTP step"))
        } else {
          val request = wsClient.url(apiConfig.url).withHttpHeaders(httpMeta.headers.getOrElse(Map.empty).toSeq: _*)
          val responseFuture = apiConfig.method match {
            case "GET" => request.get()
            case "POST" => httpMeta.body.map(b => request.post(b)).getOrElse(request.post(""))
            case "PUT" => httpMeta.body.map(b => request.put(b)).getOrElse(request.put(""))
            case "DELETE" => request.delete()
            case _ =>
              Future.failed(new IllegalArgumentException(s"Unsupported HTTP method: ${apiConfig.method}"))
          }

          responseFuture.map { response =>
            val statusStr = response.status.toString
            val httpResponse = HttpResponse(
              status = response.status,
              response = response.body,
              headers = response.headers.view.mapValues(_.head).toMap
            )

            if (httpMeta.expectedStatus.nonEmpty && httpMeta.expectedStatus.get != statusStr) {
              logger.info(s"Expected status not matching for HTTP request: expected=${httpMeta.expectedStatus.get}, actual=${statusStr}")
              return Future.successful(StepResponse(
                name = step.name,
                id = step.id.getOrElse(""),
                status = StepStatus.ERROR,
                response = StepError(
                  expectedValue = httpMeta.expectedStatus,
                  actualValue = Some(statusStr),
                  error = "Status code not matching"
                )
              ))
            }

            if (httpMeta.expectedResponse.nonEmpty && httpMeta.expectedResponse.get != response.body) {
              logger.info(s"Expected response not matching for HTTP request")
              return Future.successful(StepResponse(
                name = step.name,
                id = step.id.getOrElse(""),
                status = StepStatus.ERROR,
                response = StepError(
                  expectedValue = httpMeta.expectedResponse,
                  actualValue = Some(response.body),
                  error = "Response not matching"
                )
              ))
            }

            // Return success response with HTTP response data
            StepResponse(
              name = step.name,
              id = step.id.getOrElse(""),
              status = StepStatus.SUCCESS,
              response = httpResponse
            )
          }
        }
      }
    } catch {
      case e: Exception =>
        logger.error(s"Error preparing HTTP request for step ${step.name}: ${e.getMessage}", e)
        Future.failed(e)
    }
  }
}