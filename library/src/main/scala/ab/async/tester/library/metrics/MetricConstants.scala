package ab.async.tester.library.metrics

import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram}

/**
 * Constants for Prometheus metrics
 */
object MetricConstants {
  CollectorRegistry.defaultRegistry.clear()
  // API metrics
  val API_REQUESTS: Counter = Counter.build()
    .name("api_requests_total")
    .help("Total API requests")
    .labelNames("endpoint")
    .register()
  
  val API_ERRORS: Counter = Counter.build()
    .name("api_errors_total")
    .help("Total API errors")
    .labelNames("endpoint")
    .register()
  
  val API_HISTOGRAM_Latency: Histogram = Histogram.build()
    .name("api_latency_seconds")
    .help("API latency in seconds")
    .labelNames("endpoint")
    .register()

  val AUTH_REQUESTS: Counter = Counter.build()
    .name("auth_requests_total")
    .help("Total auth requests")
    .labelNames()
    .register()

  val AUTH_ERRORS: Counter = Counter.build()
    .name("auth_errors_total")
    .help("Total auth errors")
    .labelNames()
    .register()

  val AUTH_HISTOGRAM_Latency: Histogram = Histogram.build()
    .name("auth_latency_seconds")
    .help("auth latency in seconds")
    .labelNames()
    .register()
  
  // Database metrics
  val DB_OPERATIONS: Counter = Counter.build()
    .name("db_operations_total")
    .help("Total database operations")
    .labelNames("operation", "collection", "status")
    .register()
  
  val DB_LATENCY: Histogram = Histogram.build()
    .name("db_latency_seconds")
    .help("Database latency in seconds")
    .labelNames("operation", "collection")
    .register()
  
  // Decoding metrics
  val DECODING_ERRORS: Counter = Counter.build()
    .name("decoding_errors_total")
    .help("JSON decoding errors")
    .labelNames("type")
    .register()

  val SERVICE_REQUESTS: Counter = Counter.build()
    .name("service_requests_total")
    .help("Total service requests")
    .labelNames("service", "operation")
    .register()

  val SERVICE_ERRORS: Counter = Counter.build()
    .name("service_errors_total")
    .help("Total service errors")
    .labelNames("service", "operation", "error_type")
    .register()

  val SERVICE_HISTOGRAM_Latency: Histogram = Histogram.build()
    .name("service_latency_seconds")
    .help("Service latency in seconds")
    .labelNames("service", "operation")
    .register()

  // Repository metrics
  val REPOSITORY_REQUESTS: Counter = Counter.build()
    .name("repository_requests_total")
    .help("Total repository requests")
    .labelNames("repository", "operation")
    .register()

  val REPOSITORY_ERRORS: Counter = Counter.build()
    .name("repository_errors_total")
    .help("Total repository errors")
    .labelNames("repository", "operation", "error_type")
    .register()

  val REPOSITORY_HISTOGRAM_Latency: Histogram = Histogram.build()
    .name("repository_latency_seconds")
    .help("Repository latency in seconds")
    .labelNames("repository", "operation")
    .register()
}
