package ab.async.tester.library.repository.flow

import ab.async.tester.domain.flow.Floww
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

@ImplementedBy(classOf[FlowRepositoryImpl])
trait FlowRepository {
  def findById(id: String): Future[Option[Floww]]
  def findAll(search: Option[String], flowIds: Option[List[String]], limit: Int, page: Int): Future[List[Floww]]
  def insert(flow: Floww): Future[Floww]
  def update(flow: Floww): Future[Boolean]
  def findByName(name: String): Future[Option[Floww]]
}

@Singleton
class FlowRepositoryImpl @Inject()(
                                    db: Database
                                  )(implicit ec: ExecutionContext) extends FlowRepository {

  private implicit val logger: Logger = Logger(this.getClass)
  private val repositoryName = "FlowRepository"

  class FlowTable(tag: Tag) extends Table[Floww](tag, "flows") {
    implicit val stepsColumnType: JdbcType[List[FlowStep]] with BaseTypedType[List[FlowStep]] = MappedColumnType.base[List[FlowStep], String](
      steps => steps.asJson.noSpaces,               // write as JSON
      str   => decode[List[FlowStep]](str).getOrElse(Nil) // read from JSON
    )

    def id          = column[Option[String]]("id", O.PrimaryKey)
    def name        = column[String]("name")
    def description = column[Option[String]]("description")
    def creator     = column[String]("creator")
    def steps       = column[List[FlowStep]]("steps")
    def createdAt   = column[Long]("created_at")
    def modifiedAt  = column[Long]("modified_at")
    def version     = column[Int]("flow_version")

    def * = (id, name, description, creator, steps, createdAt, modifiedAt, version) <> (
      {
        case (id, name, description, creator, steps, createdAt, modifiedAt, version) =>
//          val stepsObj = decode[List[FlowStep]](steps).getOrElse(Nil)
          Floww(id, name, description, creator, steps, createdAt, modifiedAt, version)
      },
      (f: Floww) => {
        Some((f.id, f.name, f.description, f.creator, f.steps, f.createdAt, f.modifiedAt, f.version))
      }
    )
  }

  private val flows = TableQuery[FlowTable]

  override def findById(id: String): Future[Option[Floww]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findById") {
      db.run(flows.filter(_.id === id).result.headOption)
    }
  }

  override def findByName(name: String): Future[Option[Floww]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findByName") {
      db.run(flows.filter(_.name === name).result.headOption)
    }
  }

  override def findAll(search: Option[String], flowIds: Option[List[String]], limit: Int, page: Int): Future[List[Floww]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findAll") {
      var query = flows.drop(page * limit).take(limit)

      search.filter(_.nonEmpty).foreach { s =>
        query = query.filter(_.name.toLowerCase like s"%${s.toLowerCase}%")
      }

      flowIds.filter(_.nonEmpty).foreach { ids =>
        query = query.filter(_.id inSet ids)
      }

      db.run(query.result).map(_.toList)
    }
  }

  override def insert(flow: Floww): Future[Floww] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "insert") {
      val flowWithId = flow.copy(id = flow.id.orElse(Option(java.util.UUID.randomUUID().toString)))
      db.run(flows += flowWithId).map(_ => flowWithId)
    }
  }

  override def update(flow: Floww): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "update") {
      flow.id match {
        case Some(fid) =>
          db.run(flows.filter(_.id === fid).update(flow)).map(_ > 0)
        case None =>
          logger.error("Cannot update flow without ID")
          Future.successful(false)
      }
    }
  }
}
