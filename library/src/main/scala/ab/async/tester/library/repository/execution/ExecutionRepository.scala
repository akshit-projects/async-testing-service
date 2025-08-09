package ab.async.tester.library.repository.execution

import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.execution.{Execution, ExecutionStep}
import ab.async.tester.library.utils.{DecodingUtils, MetricUtils}
import com.google.inject.{ImplementedBy, Inject, Singleton}
import io.circe.syntax.EncoderOps
import play.api.Logger
import slick.jdbc.PostgresProfile.api._

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
  def updateStatus(id: String, status: ExecutionStatus): Future[Boolean]
}

class ExecutionTable(tag: Tag) extends Table[Execution](tag, "executions") {

  implicit private val logger: Logger = Logger(this.getClass)

  implicit val executionStatusColumnType: BaseColumnType[ExecutionStatus] =
    MappedColumnType.base[ExecutionStatus, String](
      {
        case ExecutionStatus.Todo        => "TODO"
        case ExecutionStatus.InProgress  => "IN_PROGRESS"
        case ExecutionStatus.Completed   => "COMPLETED"
        case ExecutionStatus.Failed      => "FAILED"
      },
      {
        case "TODO"         => ExecutionStatus.Todo
        case "IN_PROGRESS"  => ExecutionStatus.InProgress
        case "COMPLETED"    => ExecutionStatus.Completed
        case "FAILED"       => ExecutionStatus.Failed
        case other          => throw new IllegalArgumentException(s"Unknown status: $other")
      }
    )

  def id           = column[String]("id", O.PrimaryKey, O.Default(java.util.UUID.randomUUID().toString))
  def flowId           = column[String]("flowId")
  def flowVersion           = column[Int]("flowId")
  def status       = column[ExecutionStatus]("status")
  def startedAt       = column[Instant]("startedAt")
  def completedAt       = column[Option[Instant]]("completedAt")
  def steps       =       column[String]("steps")
  def updatedAt = column[Instant]("updatedAt")
  def parameters    = column[Option[String]]("parameters")

  def * = (id.?, flowId, flowVersion, status, startedAt, completedAt, steps, updatedAt, parameters) <> (
    { case (id, flowId, flowVersion, status, startedAt, completedAt, steps, updatedAt, parameters) =>
      val stepsObj = DecodingUtils.decodeWithErrorLogs[List[ExecutionStep]](steps)
      val params = parameters.flatMap(DecodingUtils.decodeWithErrorLogs[Option[Map[String, String]]](_))
      Execution(id.toString, flowId, flowVersion, status, startedAt, completedAt, stepsObj, updatedAt, params)
    },
    (e: Execution) => {
      val stepsStr = e.steps.asJson.noSpaces
      val params = e.parameters.map(_.asJson.noSpaces)
      Option(
        (Option(e.id), e.flowId, e.flowVersion, e.status, e.startedAt, e.completedAt, stepsStr, e.updatedAt, params)
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

  override def updateStatus(id: String, status: ExecutionStatus): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "updateStatus") {
//      val now = Instant.now() // use current Instant
//      val action = executions
//        .filter(_.id === id)
//        .map(e => (e.status, e.updatedAt))
//        .update((status, now))
//      db.run(action).map(_ > 0)
      Future.successful(false) // TODO fix this
    }
  }
}