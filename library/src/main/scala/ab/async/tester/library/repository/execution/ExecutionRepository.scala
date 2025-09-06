package ab.async.tester.library.repository.execution

import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.execution.{Execution, ExecutionStep}
import ab.async.tester.library.utils.{DecodingUtils, MetricUtils}
import com.google.inject.{ImplementedBy, Inject, Singleton}
import io.circe.syntax.EncoderOps
import io.circe.generic.auto._
import play.api.Logger
import slick.jdbc.PostgresProfile.api._
import ab.async.tester.library.repository.execution.ExecutionTable.instantColumnType
import ab.async.tester.library.repository.execution.ExecutionTable.executionStatusColumnType

import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/**
 * Repository for flow execution persistence
 */
@ImplementedBy(classOf[ExecutionRepositoryImpl])
trait ExecutionRepository {
  def saveExecution(execution: Execution): Future[Execution]
  def findById(id: String): Future[Option[Execution]]
  def getExecutions(pageNumber: Int, pageSize: Int, statuses: Option[List[ExecutionStatus]]): Future[(List[Execution], Int)]
  def updateStatus(id: String, status: ExecutionStatus, isCompleted: Boolean = false): Future[Boolean]
  def updateExecutionStep(id: String, stepId: String, step: ExecutionStep): Future[Boolean]
}

object ExecutionTable {
  implicit val executionStatusColumnType: BaseColumnType[ExecutionStatus] =
    MappedColumnType.base[ExecutionStatus, String](
      {
        case ExecutionStatus.Todo       => "TODO"
        case ExecutionStatus.InProgress => "IN_PROGRESS"
        case ExecutionStatus.Completed  => "COMPLETED"
        case ExecutionStatus.Failed     => "FAILED"
      },
      {
        case "TODO"        => ExecutionStatus.Todo
        case "IN_PROGRESS" => ExecutionStatus.InProgress
        case "COMPLETED"   => ExecutionStatus.Completed
        case "FAILED"      => ExecutionStatus.Failed
        case other         => throw new IllegalArgumentException(s"Unknown status: $other")
      }
    )

  implicit val instantColumnType: BaseColumnType[Instant] =
    MappedColumnType.base[Instant, java.sql.Timestamp](
      inst => java.sql.Timestamp.from(inst),
      ts   => ts.toInstant
    )
}

class ExecutionTable(tag: Tag) extends Table[Execution](tag, "executions") {

  implicit private val logger: Logger = Logger(this.getClass)

  def id           = column[String]("id", O.PrimaryKey, O.Default(java.util.UUID.randomUUID().toString))
  def flowId           = column[String]("flowid") // TODO fix casing of flowid
  def flowVersion      = column[Int]("flowversion")
  def status       = column[ExecutionStatus]("status")
  def startedAt       = column[Instant]("startedat")
  def completedAt       = column[Option[Instant]]("completedat")
  def steps       =       column[String]("steps")
  def updatedAt = column[Instant]("updatedat")
  def parameters    = column[Option[String]]("parameters")
  def testSuiteExecutionId = column[Option[String]]("testsuiteexecutionid")

  def * = (id.?, flowId, flowVersion, status, startedAt, completedAt, steps, updatedAt, parameters, testSuiteExecutionId) <> (
    { case (id, flowId, flowVersion, status, startedAt, completedAt, steps, updatedAt, parameters, testSuiteExecutionId) =>
      val stepsObj = DecodingUtils.decodeWithErrorLogs[List[ExecutionStep]](steps)
      val params = parameters.flatMap(DecodingUtils.decodeWithErrorLogs[Option[Map[String, String]]](_))
      Execution(id.get, flowId, flowVersion, status, startedAt, completedAt, stepsObj, updatedAt, params, testSuiteExecutionId)
    },
    (e: Execution) => {
      val stepsStr = e.steps.asJson.noSpaces
      val params = e.parameters.map(_.asJson.noSpaces)
      Option(
        (Option(e.id), e.flowId, e.flowVersion, e.status, e.startedAt, e.completedAt, stepsStr, e.updatedAt, params, e.testSuiteExecutionId)
      )
    }
  )
}

@Singleton
class ExecutionRepositoryImpl @Inject()(db: Database)(implicit ec: ExecutionContext) extends ExecutionRepository {
  private val executions = TableQuery[ExecutionTable]
  private val repositoryName = "ExecutionRepository"
  implicit private val logger: Logger = Logger(this.getClass)

  override def saveExecution(execution: Execution): Future[Execution] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "saveExecution") {
      val action = (executions returning executions.map(_.id) into ((exec, id) => exec.copy(id = id.toString))) += execution
      db.run(action)
    }
  }

  override def findById(id: String): Future[Option[Execution]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findById") {
      db.run(executions.filter(_.id === id).result.headOption)
    }
  }

  override def updateStatus(id: String, status: ExecutionStatus, isCompleted: Boolean = false): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updateStatus") {
      val now = Instant.now()
      val execution = executions
        .filter(_.id === id)
      val action = if (isCompleted) {
        execution.map(e => (e.status, e.updatedAt, e.completedAt))
          .update((status, now, Some(Instant.now())))
      } else {
        execution.map(e => (e.status, e.updatedAt))
          .update((status, now))
      }


      db.run(action).map(_ > 0) // return true if at least 1 row was updated
    }
  }

  override def getExecutions(
                              pageNumber: Int,
                              pageSize: Int,
                              statuses: Option[List[ExecutionStatus]]
                            ): Future[(List[Execution], Int)] = {
    val baseQuery = statuses match {
      case Some(s) if s.nonEmpty =>
        executions.filter(_.status.inSet(s)) // use inSet (binds automatically for enums with BaseColumnType)
      case _ =>
        executions
    }

    val query = baseQuery
      .sortBy(_.updatedAt.desc)
      .drop(pageNumber * pageSize) // page offset
      .take(pageSize)

    val countResponse = db.run(baseQuery.length.result)
    val executionsResponse = db.run(query.result).map(_.toList)

    for {
      count <- countResponse
      executions <- executionsResponse
    } yield {
      (executions, count)
    }
  }

  override def updateExecutionStep(id: String, stepId: String, step: ExecutionStep): Future[Boolean] = {
    for {
      exec <- db.run(executions.filter(_.id === id).result.head)
      updatedSteps = {
        val stepIndex = exec.steps.indexWhere(_.id.get == stepId)
        exec.steps.updated(stepIndex, step)
      }
      res <- {
        val action = executions
          .filter(_.id === id).map(e => e.steps)
            .update(updatedSteps.asJson.noSpaces)
        db.run(action)
      }
    } yield {
      res > 0
    }
  }
}