package ab.async.tester.library.repository.flow

import ab.async.tester.domain.flow.Floww
import ab.async.tester.domain.step.FlowStep
import ab.async.tester.domain.variable.FlowVariable
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Singleton}
import io.circe.jawn.decode
import io.circe.syntax.EncoderOps
import play.api.Logger
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import ab.async.tester.library.driver.MyPostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[FlowRepositoryImpl])
trait FlowRepository {
  def findById(id: String): Future[Option[Floww]]
  def findAll(
      search: Option[String],
      flowIds: Option[List[String]],
      orgId: Option[String],
      teamId: Option[String],
      limit: Int,
      page: Int
  ): Future[(List[Floww], Long)]
  def insert(flow: Floww): Future[Floww]
  def update(flow: Floww): Future[Boolean]
  def findByName(name: String): Future[Option[Floww]]
  def insertAll(flows: List[Floww]): Future[List[Floww]]
}

@Singleton
class FlowRepositoryImpl @Inject() (
    db: Database
)(implicit ec: ExecutionContext)
    extends FlowRepository {

  private implicit val logger: Logger = Logger(this.getClass)
  private val repositoryName = "FlowRepository"

  class FlowTable(tag: Tag) extends Table[Floww](tag, "flows") {
    implicit val stepsColumnType
        : JdbcType[List[FlowStep]] with BaseTypedType[List[FlowStep]] =
      MappedColumnType.base[List[FlowStep], String](
        steps => steps.asJson.noSpaces, // write as JSON
        str => decode[List[FlowStep]](str).getOrElse(Nil) // read from JSON
      )

    implicit val flowVariableColumnType
        : JdbcType[List[FlowVariable]] with BaseTypedType[List[FlowVariable]] =
      MappedColumnType.base[List[FlowVariable], String](
        variables => variables.asJson.noSpaces,
        str => decode[List[FlowVariable]](str).getOrElse(Nil)
      )

    def id = column[Option[String]]("id", O.PrimaryKey)
    def name = column[String]("name")
    def description = column[Option[String]]("description")
    def creator = column[String]("creator")
    def steps = column[List[FlowStep]]("steps")
    def variables = column[Option[List[FlowVariable]]]("variables")
    def createdAt = column[Long]("created_at")
    def modifiedAt = column[Long]("modified_at")
    def version = column[Int]("flow_version")
    def orgId = column[Option[String]]("org_id")
    def teamId = column[Option[String]]("team_id")

    def * = (
      id,
      name,
      description,
      creator,
      steps,
      variables,
      createdAt,
      modifiedAt,
      version,
      orgId,
      teamId
    ) <> (
      {
        case (
              id,
              name,
              description,
              creator,
              steps,
              variables,
              createdAt,
              modifiedAt,
              version,
              orgId,
              teamId
            ) =>
          Floww(
            id,
            name,
            description,
            creator,
            steps,
            variables.getOrElse(List.empty),
            createdAt,
            modifiedAt,
            version,
            orgId,
            teamId
          )
      },
      (f: Floww) => {
        Some(
          (
            f.id,
            f.name,
            f.description,
            f.creator,
            f.steps,
            Some(f.variables),
            f.createdAt,
            f.modifiedAt,
            f.version,
            f.orgId,
            f.teamId
          )
        )
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

  private def buildFlowQuery(
      search: Option[String],
      flowIds: Option[List[String]],
      orgId: Option[String],
      teamId: Option[String]
  ) = {
    var query = flows.asInstanceOf[Query[FlowTable, Floww, Seq]]

    search.filter(_.nonEmpty).foreach { s =>
      query =
        query.filter(f => (f.name ilike s"%$s%") || (f.creator ilike s"%$s%"))
    }

    flowIds.filter(_.nonEmpty).foreach { ids =>
      query = query.filter(_.id inSet ids)
    }

    orgId.foreach { orgIdValue =>
      query = query.filter(_.orgId === Option(orgIdValue))
    }

    teamId.foreach { teamIdValue =>
      query = query.filter(_.teamId === Option(teamIdValue))
    }

    query
  }

  override def findAll(
      search: Option[String],
      flowIds: Option[List[String]],
      orgId: Option[String],
      teamId: Option[String],
      limit: Int,
      page: Int
  ): Future[(List[Floww], Long)] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findAllWithCount") {
      val query = buildFlowQuery(search, flowIds, orgId, teamId)

      val countQuery = query.length
      val dataQuery = query.drop(page * limit).take(limit)

      // Execute both queries in parallel for better performance
      val countFuture = db.run(countQuery.result)
      val dataFuture = db.run(dataQuery.result)

      for {
        count <- countFuture
        data <- dataFuture
      } yield (data.toList, count.toLong)
    }
  }

  override def insert(flow: Floww): Future[Floww] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "insert") {
      val flowWithId = flow.copy(id =
        flow.id.orElse(Option(java.util.UUID.randomUUID().toString))
      )
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

  override def insertAll(flowsList: List[Floww]): Future[List[Floww]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "insertAll") {
      val flowsWithIds = flowsList.map(f =>
        f.copy(id = f.id.orElse(Option(java.util.UUID.randomUUID().toString)))
      )
      db.run((flows ++= flowsWithIds).transactionally).map(_ => flowsWithIds)
    }
  }
}
