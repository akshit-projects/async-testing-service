package ab.async.tester.workers.app.runner

import ab.async.tester.domain.enums.StepStatus
import ab.async.tester.domain.execution.ExecutionStep
import ab.async.tester.domain.resource.APISchemaConfig
import ab.async.tester.domain.step.{HttpResponse, HttpStepMeta, StepError, StepResponse}
import ab.async.tester.library.repository.resource.ResourceRepository
import ab.async.tester.library.substitution.VariableSubstitutionService
import ab.async.tester.workers.app.matchers.ResponseMatcher
import com.google.inject.{Inject, Singleton}
import play.api.libs.ws.StandaloneWSClient

import scala.concurrent.{ExecutionContext, Future}


/**
 * HTTP step runner for making HTTP requests
 */
@Singleton
class HttpStepRunner @Inject()(
  wsClient: StandaloneWSClient,
  resourceRepository: ResourceRepository,
  protected val variableSubstitutionService: VariableSubstitutionService
)(implicit ec: ExecutionContext) extends BaseStepRunner {
  override protected val runnerName: String = "HttpStepRunner"

  override protected def executeStep(step: ExecutionStep, previousResults: List[StepResponse]): Future[StepResponse] = {
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
              logger.info(s"Expected status not matching for HTTP request: expected=${httpMeta.expectedStatus.get}, actual=$statusStr")
              StepResponse(
                name = step.name,
                id = step.id.getOrElse(""),
                status = StepStatus.ERROR,
                response = StepError(
                  expectedValue = httpMeta.expectedStatus,
                  actualValue = Some(statusStr),
                  error = "Status code not matching"
                )
              )
            } else if (httpMeta.expectedResponse.exists(_.nonEmpty)) {
              val expectedBody = httpMeta.expectedResponse.get
              val actualBody = response.body
              // Get the content type, defaulting to None if not present
              val contentType = response.headers.get("Content-Type").flatMap(_.headOption)

              if (!ResponseMatcher.matches(expectedBody, actualBody, contentType)) {
                logger.info(s"Expected response not matching for HTTP request. Content-Type: ${contentType.getOrElse("N/A")}")
                StepResponse(
                  name = step.name,
                  id = step.id.getOrElse(""),
                  status = StepStatus.ERROR,
                  response = StepError(
                    expectedValue = httpMeta.expectedResponse,
                    actualValue = Some(actualBody),
                    error = "Response body not matching expected value or structure"
                  )
                )
              } else {
                StepResponse(
                  name = step.name,
                  id = step.id.getOrElse(""),
                  status = StepStatus.SUCCESS,
                  response = httpResponse
                )
              }
            } else {
              StepResponse(
                name = step.name,
                id = step.id.getOrElse(""),
                status = StepStatus.SUCCESS,
                response = httpResponse
              )
            }
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