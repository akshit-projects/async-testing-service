package ab.async.tester.library.repository.flow

import ab.async.tester.domain.flow.Floww
import ab.async.tester.library.utils.{MetricUtils}
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Logger

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
    def id          = column[Option[String]]("id", O.PrimaryKey)
    def name        = column[String]("name")
    def description = column[Option[String]]("description")
    def creator     = column[String]("creator")
    def createdAt   = column[Long]("created_at")
    def modifiedAt  = column[Long]("modified_at")

    def * = (id, name, description, creator, createdAt, modifiedAt) <> ((Floww.apply _).tupled, Floww.unapply)
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
