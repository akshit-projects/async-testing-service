package ab.async.tester.library.utils

import ab.async.tester.library.metrics.MetricConstants
import io.prometheus.client.{Counter, Histogram}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Utilities for measuring API metrics
 */
object MetricUtils {
  private val logger = Logger(this.getClass)

  // Metrics for API calls
  private val apiCallCounter: Counter = MetricConstants.API_REQUESTS
  private val apiErrorCounter: Counter = MetricConstants.API_ERRORS
  private val apiLatencyHistogram: Histogram = MetricConstants.API_HISTOGRAM_Latency

  // Metrics for service calls
  private val serviceCallCounter: Counter = MetricConstants.SERVICE_REQUESTS
  private val serviceErrorCounter: Counter = MetricConstants.SERVICE_ERRORS
  private val serviceLatencyHistogram: Histogram = MetricConstants.SERVICE_HISTOGRAM_Latency
  
  // Metrics for repository calls
  private val repositoryCallCounter: Counter = MetricConstants.REPOSITORY_REQUESTS
  private val repositoryErrorCounter: Counter = MetricConstants.REPOSITORY_ERRORS
  private val repositoryLatencyHistogram: Histogram = MetricConstants.REPOSITORY_HISTOGRAM_Latency

  /**
   * Measures API calls with metrics - synchronous version
   *
   * @param endpoint the API endpoint name
   * @param fn the function to execute and measure
   * @tparam T the return type of the function
   * @return the result of the function
   */
  def withAPIMetrics[T](endpoint: String)(fn: => T): T = {
    val timer = apiLatencyHistogram.labels(endpoint).startTimer()

    try {
      val result = fn
      apiCallCounter.labels(endpoint, "success").inc()
      result
    } catch {
      case e: Exception =>
        apiCallCounter.labels(endpoint, "error").inc()
        logger.error(s"Error in API call to $endpoint: ${e.getMessage}", e)
        throw e
    } finally {
      timer.close()
    }
  }

  def withServiceMetrics[T](serviceName: String, operation: String)(fn: => T): T = {
    serviceCallCounter.labels(serviceName, operation).inc()
    val timer = serviceLatencyHistogram.labels(serviceName, operation).startTimer()

    try {
      val result = fn
      timer.close()
      result
    } catch {
      case e: Exception =>
        timer.close()
        serviceErrorCounter.labels(serviceName, operation, e.getClass.getSimpleName).inc()
        logger.error(s"Error starting service call to $serviceName for $operation: ${e.getMessage}", e)
        throw e
    }
  }

  /**
   * Measures async API calls with metrics
   *
   * @param endpoint the API endpoint name
   * @param fn the async function to execute and measure
   * @param ec execution context for the future
   * @tparam T the return type of the function
   * @return the future result of the function
   */
  def withAsyncAPIMetrics[T](endpoint: String)(fn: => Future[T])(implicit ec: ExecutionContext, logger: Logger): Future[T] = {
    apiCallCounter.labels(endpoint).inc()
    val timer = apiLatencyHistogram.labels(endpoint).startTimer()
    
    try {
      val result = fn
      result.map { value =>
        timer.close()
        value
      }.recover {
        case e: Exception =>
          timer.close()
          apiErrorCounter.labels(endpoint, e.getClass.getSimpleName).inc()
          logger.error(s"Error in API call to $endpoint: ${e.getMessage}", e)
          throw e
      }
    } catch {
      case e: Exception =>
        timer.close()
        apiErrorCounter.labels(endpoint).inc()
        logger.error(s"Error starting API call to $endpoint: ${e.getMessage}", e)
        Future.failed(e)
    }
  }

  /**
   * Measures async service calls with metrics
   * 
   * @param serviceName the service name
   * @param operation the operation name
   * @param fn the async function to execute and measure
   * @param ec execution context for the future
   * @tparam T the return type of the function
   * @return the future result of the function
   */
  def withAsyncServiceMetrics[T](serviceName: String, operation: String)(fn: => Future[T])(implicit ec: ExecutionContext, logger: Logger): Future[T] = {
    serviceCallCounter.labels(serviceName, operation).inc()
    val timer = serviceLatencyHistogram.labels(serviceName, operation).startTimer()

    try {
      val result = fn
      result.map { value =>
        timer.close()
        value
      }.recover {
        case e: Exception =>
          timer.close()
          serviceErrorCounter.labels(serviceName, operation, e.getClass.getSimpleName).inc()
          logger.error(s"Error in service call to $serviceName for $operation: ${e.getMessage}", e)
          throw e
      }
    } catch {
      case e: Exception =>
        timer.close()
        serviceErrorCounter.labels(serviceName, operation, e.getClass.getSimpleName).inc()
        logger.error(s"Error starting service call to $serviceName for $operation: ${e.getMessage}", e)
        Future.failed(e)
    }
  }
  
  /**
   * Measures async repository calls with metrics
   * 
   * @param repositoryName the repository name
   * @param operation the operation name
   * @param fn the async function to execute and measure
   * @param ec execution context for the future
   * @tparam T the return type of the function
   * @return the future result of the function
   */
  def withAsyncRepositoryMetrics[T](repositoryName: String, operation: String)(fn: => Future[T])(implicit ec: ExecutionContext, logger: Logger): Future[T] = {
    repositoryCallCounter.labels(repositoryName, operation).inc()
    val timer = repositoryLatencyHistogram.labels(repositoryName, operation).startTimer()

    try {
      val result = fn
      result.map { value =>
        timer.close()
        value
      }.recover {
        case e: Exception =>
          timer.close()
          repositoryErrorCounter.labels(repositoryName, operation, e.getClass.getSimpleName).inc()
          logger.error(s"Error in repository call to $repositoryName for $operation: ${e.getMessage}", e)
          throw e
      }
    } catch {
      case e: Exception =>
        timer.close()
        repositoryErrorCounter.labels(repositoryName, operation, e.getClass.getSimpleName).inc()
        logger.error(s"Error starting repository call to $repositoryName for $operation: ${e.getMessage}", e)
        Future.failed(e)
    }
  }
}
