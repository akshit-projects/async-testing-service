package ab.async.tester.library.repository.flow

import ab.async.tester.domain.flow.FlowVersion
import ab.async.tester.domain.step.FlowStep
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Singleton}
import io.circe.jawn.decode
import io.circe.syntax.EncoderOps
import play.api.Logger
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._

@ImplementedBy(classOf[FlowVersionRepositoryImpl])
trait FlowVersionRepository {
  def findById(id: String): Future[Option[FlowVersion]]
  def findByFlowId(flowId: String): Future[List[FlowVersion]]
  def findByFlowIdAndVersion(flowId: String, version: Int): Future[Option[FlowVersion]]
  def findLatestByFlowId(flowId: String): Future[Option[FlowVersion]]
  def insert(flowVersion: FlowVersion): Future[FlowVersion]
  def getNextVersionNumber(flowId: String): Future[Int]
}

@Singleton
class FlowVersionRepositoryImpl @Inject()(
                                           db: Database
                                         )(implicit ec: ExecutionContext) extends FlowVersionRepository {

  private implicit val logger: Logger = Logger(this.getClass)
  private val repositoryName = "FlowVersionRepository"

  class FlowVersionTable(tag: Tag) extends Table[FlowVersion](tag, "flow_versions") {
    implicit val stepsColumnType: JdbcType[List[FlowStep]] with BaseTypedType[List[FlowStep]] = MappedColumnType.base[List[FlowStep], String](
      steps => steps.asJson.noSpaces,               // write as JSON
      str   => decode[List[FlowStep]](str).getOrElse(Nil) // read from JSON
    )

    def id          = column[Option[String]]("id", O.PrimaryKey)
    def flowId      = column[String]("flow_id")
    def version     = column[Int]("version")
    def steps       = column[List[FlowStep]]("steps")
    def createdAt   = column[Long]("created_at")
    def createdBy   = column[String]("created_by")
    def description = column[Option[String]]("description")

    def * = (id, flowId, version, steps, createdAt, createdBy, description) <> ((FlowVersion.apply _).tupled, FlowVersion.unapply)
  }

  private val flowVersions = TableQuery[FlowVersionTable]

  override def findById(id: String): Future[Option[FlowVersion]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findById") {
      db.run(flowVersions.filter(_.id === id).result.headOption)
    }
  }

  override def findByFlowId(flowId: String): Future[List[FlowVersion]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findByFlowId") {
      db.run(flowVersions.filter(_.flowId === flowId).sortBy(_.version.desc).result).map(_.toList)
    }
  }

  override def findByFlowIdAndVersion(flowId: String, version: Int): Future[Option[FlowVersion]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findByFlowIdAndVersion") {
      db.run(flowVersions.filter(fv => fv.flowId === flowId && fv.version === version).result.headOption)
    }
  }

  override def findLatestByFlowId(flowId: String): Future[Option[FlowVersion]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findLatestByFlowId") {
      db.run(flowVersions.filter(_.flowId === flowId).sortBy(_.version.desc).result.headOption)
    }
  }

  override def insert(flowVersion: FlowVersion): Future[FlowVersion] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "insert") {
      val flowVersionWithId = flowVersion.copy(id = flowVersion.id.orElse(Option(java.util.UUID.randomUUID().toString)))
      db.run(flowVersions += flowVersionWithId).map(_ => flowVersionWithId)
    }
  }

  override def getNextVersionNumber(flowId: String): Future[Int] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "getNextVersionNumber") {
      db.run(flowVersions.filter(_.flowId === flowId).map(_.version).max.result).map {
        case Some(maxVersion) => maxVersion + 1
        case None => 1
      }
    }
  }
}
