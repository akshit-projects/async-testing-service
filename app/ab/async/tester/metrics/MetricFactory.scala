package ab.async.tester.metrics

import com.typesafe.config.ConfigFactory
import io.prometheus.client.{CollectorRegistry, _}

object MetricFactory {
  private val registry = CollectorRegistry.defaultRegistry
  private val config = ConfigFactory.load()
  private val registeredMetrics = scala.collection.mutable.Map[String, Any]()

  private val metricPrefix = if (config.hasPath("metric.metricNamePrefix")) {
    config.getString("metric.metricNamePrefix").stripSuffix("_").concat("_")
  } else {
    ""
  }

  def getGaugeMetric(metricObject: MetricObject): Gauge = {
    registeredMetrics.getOrElseUpdate(metricObject.metricName,
      Gauge.build()
        .name(metricPrefix.concat(metricObject.metricName))
        .help(metricObject.description)
        .labelNames(metricObject.labels: _*)
        .register(registry)).asInstanceOf[Gauge]
  }

  def getHistogramMetric(metricObject: MetricObject): Histogram = {
    registeredMetrics.getOrElseUpdate(metricObject.metricName,
      Histogram.build()
        .name(metricPrefix.concat(metricObject.metricName))
        .help(metricObject.description)
        .labelNames(metricObject.labels: _*)
        .buckets(0.001, 0.005, 0.01, 0.05, 0.1, 0.2, 0.5, 1, 2, 5, 10, 20)
        .register(registry)).asInstanceOf[Histogram]
  }

  def getCounterMetric(metricObject: MetricObject): Counter = {
    registeredMetrics.getOrElseUpdate(metricObject.metricName,
      Counter.build()
        .name(metricPrefix.concat(metricObject.metricName))
        .help(metricObject.description)
        .labelNames(metricObject.labels: _*)
        .register(registry)).asInstanceOf[Counter]
  }

  def getSummaryMetric(metricObject: MetricObject): Summary = {
    registeredMetrics.getOrElseUpdate(metricObject.metricName,
      Summary.build()
        .name(metricPrefix.concat(metricObject.metricName))
        .help(metricObject.description)
        .labelNames(metricObject.labels: _*)
        .register(registry)).asInstanceOf[Summary]
  }
}
