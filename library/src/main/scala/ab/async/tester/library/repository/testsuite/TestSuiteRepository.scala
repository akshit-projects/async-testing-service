package ab.async.tester.library.repository.testsuite

import ab.async.tester.domain.testsuite.{TestSuite, TestSuiteFlowConfig}
import ab.async.tester.library.utils.{DecodingUtils, MetricUtils}
import com.google.inject.{ImplementedBy, Inject, Singleton}
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import play.api.Logger
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import ab.async.tester.library.driver.MyPostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

/**
 * Repository for test suite persistence
 */
@ImplementedBy(classOf[TestSuiteRepositoryImpl])
trait TestSuiteRepository {
  def findById(id: String): Future[Option[TestSuite]]
  def findAll(search: Option[String], creator: Option[String], enabled: Option[Boolean], orgId: Option[String], teamId: Option[String], limit: Int, page: Int): Future[List[TestSuite]]
  def findAllWithCount(search: Option[String], creator: Option[String], enabled: Option[Boolean], orgId: Option[String], teamId: Option[String], limit: Int, page: Int): Future[(List[TestSuite], Long)]
  def insert(testSuite: TestSuite): Future[TestSuite]
  def update(testSuite: TestSuite): Future[Boolean]
  def delete(id: String): Future[Boolean]
  def findByName(name: String): Future[Option[TestSuite]]
}

@Singleton
class TestSuiteRepositoryImpl @Inject()(
  db: Database
)(implicit ec: ExecutionContext) extends TestSuiteRepository {

  private implicit val logger: Logger = Logger(this.getClass)
  private val repositoryName = "TestSuiteRepository"

  class TestSuiteTable(tag: Tag) extends Table[TestSuite](tag, "test_suites") {
    implicit val flowsColumnType: JdbcType[List[TestSuiteFlowConfig]] with BaseTypedType[List[TestSuiteFlowConfig]] =
      MappedColumnType.base[List[TestSuiteFlowConfig], String](
        flows => flows.asJson.noSpaces,
        str => DecodingUtils.decodeWithErrorLogs[List[TestSuiteFlowConfig]](str)
      )

    def id = column[Option[String]]("id", O.PrimaryKey)
    def name = column[String]("name")
    def description = column[Option[String]]("description")
    def creator = column[String]("creator")
    def flows = column[List[TestSuiteFlowConfig]]("flows")
    def runUnordered = column[Boolean]("rununordered")
    def createdAt = column[Long]("createdat")
    def modifiedAt = column[Long]("modifiedat")
    def enabled = column[Boolean]("enabled")
    def orgId = column[Option[String]]("org_id")
    def teamId = column[Option[String]]("team_id")

    def * = (id, name, description, creator, flows, runUnordered, createdAt, modifiedAt, enabled, orgId, teamId) <> (
      {
        case (id, name, description, creator, flows, runUnordered, createdAt, modifiedAt, enabled, orgId, teamId) =>
          TestSuite(id, name, description, creator, flows, runUnordered, createdAt, modifiedAt, enabled, orgId, teamId)
      },
      (ts: TestSuite) => {
        Some((ts.id, ts.name, ts.description, ts.creator, ts.flows, ts.runUnordered, ts.createdAt, ts.modifiedAt, ts.enabled, ts.orgId, ts.teamId))
      }
    )
  }

  private val testSuites = TableQuery[TestSuiteTable]

  override def findById(id: String): Future[Option[TestSuite]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findById") {
      db.run(testSuites.filter(_.id === id).result.headOption)
    }
  }

  override def findByName(name: String): Future[Option[TestSuite]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findByName") {
      db.run(testSuites.filter(_.name === name).result.headOption)
    }
  }

  private def buildTestSuiteQuery(
      search: Option[String],
      creator: Option[String],
      enabled: Option[Boolean],
      orgId: Option[String],
      teamId: Option[String]
  ) = {
    var query = testSuites.asInstanceOf[Query[TestSuiteTable, TestSuite, Seq]]

    search.filter(_.nonEmpty).foreach { s =>
      query = query
        .filter(ts => (ts.name ilike s"%$s%") || (ts.creator ilike s"%$s%"))
    }

    creator.filter(_.nonEmpty).foreach { c =>
      query = query.filter(_.creator === c)
    }

    enabled.foreach { e =>
      query = query.filter(_.enabled === e)
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
      creator: Option[String],
      enabled: Option[Boolean],
      orgId: Option[String],
      teamId: Option[String],
      limit: Int,
      page: Int
  ): Future[List[TestSuite]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findAll") {
      val query = buildTestSuiteQuery(search, creator, enabled, orgId, teamId)
      db.run(query.drop(page * limit).take(limit).sortBy(_.modifiedAt.desc).result).map(_.toList)
    }
  }

  override def findAllWithCount(search: Option[String], creator: Option[String], enabled: Option[Boolean], orgId: Option[String], teamId: Option[String], limit: Int, page: Int): Future[(List[TestSuite], Long)] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findAllWithCount") {
      val query = buildTestSuiteQuery(search, creator, enabled, orgId, teamId)

      val countQuery = query.length
      val dataQuery = query.drop(page * limit).take(limit).sortBy(_.modifiedAt.desc)

      // Execute both queries in parallel for better performance
      val countFuture = db.run(countQuery.result)
      val dataFuture = db.run(dataQuery.result)

      for {
        count <- countFuture
        data <- dataFuture
      } yield (data.toList, count.toLong)
    }
  }

  override def insert(testSuite: TestSuite): Future[TestSuite] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "insert") {
      val testSuiteWithId = testSuite.copy(id = testSuite.id.orElse(Option(java.util.UUID.randomUUID().toString)))
      db.run(testSuites += testSuiteWithId).map(_ => testSuiteWithId)
    }
  }

  override def update(testSuite: TestSuite): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "update") {
      testSuite.id match {
        case Some(tsId) =>
          val updatedTestSuite =
            testSuite.copy(modifiedAt = System.currentTimeMillis())
          db.run(testSuites.filter(_.id === tsId).update(updatedTestSuite))
            .map(_ > 0)
        case None =>
          logger.error("Cannot update test suite without ID")
          Future.successful(false)
      }
    }
  }

  override def delete(id: String): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "delete") {
      db.run(testSuites.filter(_.id === id).delete).map(_ > 0)
    }
  }
}
