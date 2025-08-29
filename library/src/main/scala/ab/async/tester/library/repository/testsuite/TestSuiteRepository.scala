package ab.async.tester.library.repository.testsuite

import ab.async.tester.domain.testsuite.{TestSuite, TestSuiteFlowConfig}
import ab.async.tester.library.utils.{DecodingUtils, MetricUtils}
import com.google.inject.{ImplementedBy, Inject, Singleton}
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import play.api.Logger
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

/**
 * Repository for test suite persistence
 */
@ImplementedBy(classOf[TestSuiteRepositoryImpl])
trait TestSuiteRepository {
  def findById(id: String): Future[Option[TestSuite]]
  def findAll(search: Option[String], creator: Option[String], enabled: Option[Boolean], limit: Int, page: Int): Future[List[TestSuite]]
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

    def * = (id, name, description, creator, flows, runUnordered, createdAt, modifiedAt, enabled) <> (
      {
        case (id, name, description, creator, flows, runUnordered, createdAt, modifiedAt, enabled) =>
          TestSuite(id, name, description, creator, flows, runUnordered, createdAt, modifiedAt, enabled)
      },
      (ts: TestSuite) => {
        Some((ts.id, ts.name, ts.description, ts.creator, ts.flows, ts.runUnordered, ts.createdAt, ts.modifiedAt, ts.enabled))
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

  override def findAll(search: Option[String], creator: Option[String], enabled: Option[Boolean], limit: Int, page: Int): Future[List[TestSuite]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findAll") {
      var query = testSuites.drop(page * limit).take(limit)

      search.filter(_.nonEmpty).foreach { s =>
        query = query.filter(_.name.toLowerCase like s"%${s.toLowerCase}%")
      }

      creator.filter(_.nonEmpty).foreach { c =>
        query = query.filter(_.creator === c)
      }

      enabled.foreach { e =>
        query = query.filter(_.enabled === e)
      }

      db.run(query.sortBy(_.modifiedAt.desc).result).map(_.toList)
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
          val updatedTestSuite = testSuite.copy(modifiedAt = System.currentTimeMillis())
          db.run(testSuites.filter(_.id === tsId).update(updatedTestSuite)).map(_ > 0)
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
