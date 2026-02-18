package ab.async.tester.workers.app.runner

import ab.async.tester.domain.enums.StepStatus
import ab.async.tester.domain.execution.ExecutionStep
import ab.async.tester.domain.resource.LokiResourceConfig
import ab.async.tester.domain.step.metas.LokiStepMeta
import ab.async.tester.domain.step.{
  LokiResponse,
  LogEntry,
  StepError,
  StepResponse
}
import ab.async.tester.library.repository.resource.ResourceRepository
import ab.async.tester.library.substitution.VariableSubstitutionService
import com.google.inject.{Inject, Singleton}
import io.circe.parser._
import play.api.libs.ws.{StandaloneWSClient, StandaloneWSResponse}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LokiStepRunner @Inject() (
    protected val variableSubstitutionService: VariableSubstitutionService,
    ws: StandaloneWSClient
)(implicit ec: ExecutionContext, resourceRepository: ResourceRepository)
    extends BaseStepRunner {

  override protected val runnerName: String = "LokiStepRunner"

  override protected def executeStep(
      step: ExecutionStep,
      previousResults: List[StepResponse]
  ): Future[StepResponse] = {
    val lokiMeta = extractMeta(step)
    val startTime = System.currentTimeMillis()

    fetchLokiConfig(lokiMeta.resourceId, step.name)
      .flatMap { lokiConfig =>
        fetchLogs(lokiConfig, lokiMeta).map { response =>
          handleResponse(step, lokiMeta, startTime, response)
        }
      }
      .recover { case ex: Exception =>
        handleError(step, ex)
      }
  }

  private def extractMeta(step: ExecutionStep): LokiStepMeta = {
    step.meta match {
      case meta: LokiStepMeta => meta
      case _                  =>
        throw new IllegalStateException(
          s"Invalid step meta found for Loki log search for step ${step.name}"
        )
    }
  }

  private def fetchLokiConfig(
      resourceId: String,
      stepName: String
  ): Future[LokiResourceConfig] = {
    resourceRepository.findById(resourceId).map {
      case Some(config: LokiResourceConfig) => config
      case Some(_)                          =>
        throw new IllegalStateException(
          s"The resourceId is not valid for step: $stepName"
        )
      case None =>
        throw new IllegalStateException(
          s"The resource for step: $stepName is not found"
        )
    }
  }

  private def fetchLogs(
      lokiConfig: LokiResourceConfig,
      lokiMeta: LokiStepMeta
  ): Future[StandaloneWSResponse] = {
    val logQLQuery = buildLogQLQuery(lokiMeta)
    val queryUrl = s"${lokiConfig.url}/loki/api/v1/query_range"

    logger.info(s"Executing Loki query: $logQLQuery on $queryUrl")

    val (start, end) = resolveTimeRange(lokiMeta)

    var request = ws
      .url(queryUrl)
      .withRequestTimeout(
        scala.concurrent.duration.Duration(lokiConfig.timeout.getOrElse(30000), "ms")
      )
      .addQueryStringParameters(
        "query" -> logQLQuery,
        "limit" -> lokiMeta.limit.toString
      )

    request = start.fold(request)(s =>
      request.addQueryStringParameters("start" -> s.toString)
    )
    request = end.fold(request)(e =>
      request.addQueryStringParameters("end" -> e.toString)
    )

    lokiConfig.authToken.foreach { token =>
      request = request.addHttpHeaders("Authorization" -> s"Bearer $token")
    }

    request.get()
  }

  private def handleResponse(
      step: ExecutionStep,
      lokiMeta: LokiStepMeta,
      startTime: Long,
      response: StandaloneWSResponse
  ): StepResponse = {
    if (response.status == 200) {
      parseLokiResponse(response.body) match {
        case Right(logEntries) =>
          val filteredEntries = filterLogEntries(logEntries, lokiMeta)
          val executionTime = System.currentTimeMillis() - startTime
          buildSuccessResponse(
            step,
            filteredEntries,
            response.body.length.toLong,
            executionTime
          )
        case Left(error) =>
          logger.error(
            s"Error parsing Loki response for step ${step.name}: $error"
          )
          buildErrorResponse(step, s"Failed to parse Loki response: $error")
      }
    } else {
      logger.error(
        s"Loki query failed for step ${step.name}: ${response.status} - ${response.body}"
      )
      buildErrorResponse(
        step,
        s"Loki query failed: ${response.status} - ${response.body}"
      )
    }
  }

  private def parseLokiResponse(
      body: String
  ): Either[String, List[LogEntry]] = {
    parse(body)
      .flatMap { json =>
        json.hcursor
          .downField("data")
          .downField("result")
          .as[List[io.circe.Json]]
          .map { streams =>
            streams.flatMap(parseStream)
          }
      }
      .left
      .map(_.getMessage)
  }

  private def parseStream(stream: io.circe.Json): List[LogEntry] = {
    val cursor = stream.hcursor
    val labels =
      cursor.downField("stream").as[Map[String, String]].getOrElse(Map.empty)
    val values =
      cursor.downField("values").as[List[List[String]]].getOrElse(List.empty)

    values.map { value =>
      val timestamp = value.headOption
        .flatMap(v => scala.util.Try(v.toLong).toOption)
        .getOrElse(0L)
      val line = value.lift(1).getOrElse("")
      LogEntry(timestamp, line, labels)
    }
  }

  private def buildSuccessResponse(
      step: ExecutionStep,
      entries: List[LogEntry],
      scannedBytes: Long,
      timeMs: Long
  ): StepResponse = {
    StepResponse(
      name = step.name,
      id = step.id.getOrElse(""),
      status = StepStatus.SUCCESS,
      response = LokiResponse(
        logLines = entries,
        matchCount = entries.size,
        scannedBytes = scannedBytes,
        executionTimeMs = timeMs
      )
    )
  }

  private def buildErrorResponse(
      step: ExecutionStep,
      message: String
  ): StepResponse = {
    StepResponse(
      name = step.name,
      id = step.id.getOrElse(""),
      status = StepStatus.ERROR,
      response = StepError(message, None, None)
    )
  }

  private def handleError(step: ExecutionStep, ex: Throwable): StepResponse = {
    logger.error(
      s"Exception during Loki query for step ${step.name}: ${ex.getMessage}",
      ex
    )
    buildErrorResponse(step, ex.getMessage)
  }

  private def resolveTimeRange(
      lokiMeta: LokiStepMeta
  ): (Option[Long], Option[Long]) = {
    lokiMeta.relativeTime match {
      case Some(relative) =>
        val durationMs = parseRelativeTime(relative)
        val end = System.currentTimeMillis()
        val start = end - durationMs
        (
          Some(start * 1000000),
          Some(end * 1000000)
        ) // Loki expects nanoseconds for timestamps or strings like "5m"
      case None =>
        // Convert milliseconds to nanoseconds for Loki if provided
        (lokiMeta.startTime.map(_ * 1000000), lokiMeta.endTime.map(_ * 1000000))
    }
  }

  private def parseRelativeTime(relative: String): Long = {
    val durationPattern =
      """(?i)last\s+(\d+)\s*(minute|minutes|hour|hours|day|days|second|seconds|m|h|d|s)""".r
    val simplePattern = """(\d+)(m|h|d|s)""".r

    val (value, unit) = relative.toLowerCase match {
      case durationPattern(v, u) => (v.toLong, u)
      case simplePattern(v, u)   => (v.toLong, u)
      case _                     =>
        logger.warn(
          s"Invalid relative time format: $relative, defaulting to 5 minutes"
        )
        (5L, "m")
    }

    unit match {
      case "m" | "minute" | "minutes" => value * 60 * 1000
      case "h" | "hour" | "hours"     => value * 60 * 60 * 1000
      case "d" | "day" | "days"       => value * 24 * 60 * 60 * 1000
      case "s" | "second" | "seconds" => value * 1000
      case _                          => value * 60 * 1000
    }
  }

  /** Build LogQL query from step metadata Example:
    * {namespace="production",app="api-service"} |= "error" |= "exception" !=
    * "test"
    */
  private def buildLogQLQuery(lokiMeta: LokiStepMeta): String = {
    // Build label matchers
    val labelSelectors = lokiMeta.labels
      .map { case (k, v) =>
        s"""$k="$v""""
      }
      .mkString(",")

    val baseQuery = if (labelSelectors.nonEmpty) s"{$labelSelectors}" else "{}"

    // Add contains patterns (|= "pattern")
    val withContains = lokiMeta.containsPatterns.foldLeft(baseQuery) {
      (query, pattern) =>
        s"""$query |= `$pattern`"""
    }

    // Add notContains patterns (!= "pattern")
    val withNotContains = lokiMeta.notContainsPatterns.foldLeft(withContains) {
      (query, pattern) =>
        s"""$query != `$pattern`"""
    }

    withNotContains
  }

  /** Filter log entries based on contains and notContains patterns This
    * provides additional client-side filtering beyond LogQL
    */
  private def filterLogEntries(
      entries: List[LogEntry],
      lokiMeta: LokiStepMeta
  ): List[LogEntry] = {
    entries.filter { entry =>
      val line = entry.line

      // Check all containsPatterns are present
      val containsMatch =
        lokiMeta.containsPatterns.forall(pattern => line.contains(pattern))

      // Check none of the notContainsPatterns are present
      val notContainsMatch =
        !lokiMeta.notContainsPatterns.exists(pattern => line.contains(pattern))

      containsMatch && notContainsMatch
    }
  }
}
