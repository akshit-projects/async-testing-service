package ab.async.tester.controllers

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import javax.inject.{Inject, Singleton}
import play.api.mvc.{
  AbstractController,
  Action,
  AnyContent,
  ControllerComponents
}

import java.io.{StringWriter, Writer}

@Singleton
class MetricsController @Inject() (cc: ControllerComponents)
    extends AbstractController(cc) {

  /** Exposes Prometheus metrics endpoint
    *
    * @return
    *   the metrics in Prometheus text format
    */
  def metrics: Action[AnyContent] = Action { request =>
    val writer = new StringWriter()
    writeMetrics(writer)
    Ok(writer.toString).as("text/plain; version=0.0.4")
  }

  /** Writes metrics to the writer in Prometheus text format
    */
  private def writeMetrics(writer: Writer): Unit = {
    TextFormat.write004(
      writer,
      CollectorRegistry.defaultRegistry.metricFamilySamples()
    )
  }
}
