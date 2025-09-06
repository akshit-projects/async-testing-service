package ab.async.tester.library.repository.resource

import ab.async.tester.domain.resource._
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Singleton}
import io.circe.syntax.EncoderOps
import play.api.Logger
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{ProvenShape, Tag}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Repository for resource configuration persistence
 */
@ImplementedBy(classOf[ResourceRepositoryImpl])
trait ResourceRepository {
  def findById(id: String): Future[Option[ResourceConfig]]
  def findAll(typesOpt: Option[List[String]], groupOpt: Option[String], namespaceOpt: Option[String]): Future[List[ResourceConfig]]
  def findAllWithCount(typesOpt: Option[List[String]], groupOpt: Option[String], namespaceOpt: Option[String], limit: Int, page: Int): Future[(List[ResourceConfig], Long)]
  def create(resource: ResourceConfig): Future[ResourceConfig]
  def update(resource: ResourceConfig): Future[Option[ResourceConfig]]
  def delete(id: String): Future[Boolean]
  def findByUniqueFields(resource: ResourceConfig): Future[Option[ResourceConfig]]
}


// === Slick table mapping ===
class ResourceConfigTable(tag: Tag) extends Table[ResourceConfig](tag, "resource_configs") {
  def id = column[String]("id", O.PrimaryKey)
  def name = column[String]("name")
  def `type` = column[String]("type")
  def group = column[Option[String]]("group")
  def namespace = column[Option[String]]("namespace")
  def payload = column[String]("payload_json")

  def * : ProvenShape[ResourceConfig] =
    (id, name, group, namespace, `type`, payload).<>(
      // Apply
      { case (id, name, g, ns, t, p) =>
        ResourceConfig.fromDb(p)
      },
      // Unapply
      (rc: ResourceConfig) => Some((rc.getId, rc.getType, rc.group, rc.getNamespace, rc.getType, rc.asJson.noSpaces))
    )
}


// === Implementation ===
@Singleton
class ResourceRepositoryImpl @Inject()(db: Database)(implicit ec: ExecutionContext) extends ResourceRepository {
  private implicit val logger: Logger = Logger(this.getClass)
  private val table = TableQuery[ResourceConfigTable]
  private val repositoryName = "ResourceRepository"

  override def findById(id: String): Future[Option[ResourceConfig]] =
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findById") {
      val q = table.filter(_.id === id).result.headOption
      db.run(q)
    }

  private def buildResourceQuery(typesOpt: Option[List[String]], groupOpt: Option[String], namespaceOpt: Option[String]) = {
    var query = table.map(t => t)

    typesOpt.foreach { types => query = query.filter(_.`type`.inSet(types)) }
    groupOpt.foreach { g => query = query.filter(_.group === Option(g)) }
    namespaceOpt.foreach { ns => query = query.filter(_.namespace === Option(ns)) }

    query
  }

  override def findAll(typesOpt: Option[List[String]], groupOpt: Option[String], namespaceOpt: Option[String]): Future[List[ResourceConfig]] =
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findAll") {
      val query = buildResourceQuery(typesOpt, groupOpt, namespaceOpt)
      db.run(query.result).map(_.toList)
    }

  override def findAllWithCount(typesOpt: Option[List[String]], groupOpt: Option[String], namespaceOpt: Option[String], limit: Int, page: Int): Future[(List[ResourceConfig], Long)] =
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findAllWithCount") {
      val query = buildResourceQuery(typesOpt, groupOpt, namespaceOpt)

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

  override def findByUniqueFields(resource: ResourceConfig): Future[Option[ResourceConfig]] =
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findByUniqueFields") {
      val query = resource match {
        case dbCfg: SQLDBConfig =>
          table.filter(t => t.name === "database" && t.payload.like(s"""%"dbUrl":"${dbCfg.dbUrl}"%"""))
        case kafkaCfg: KafkaResourceConfig =>
          table.filter(t => t.name === "kafka" && t.payload.like(s"""%"brokersList":"${kafkaCfg.brokersList}"%"""))
        case apiCfg: APISchemaConfig =>
          table.filter(t => t.name === "http" && t.payload.like(s"""%"url":"${apiCfg.url}"%"""))
        case _ =>
          table.filter(_.id === "___no_match___") // always empty
      }
      db.run(query.result.headOption)
    }

  override def create(resource: ResourceConfig): Future[ResourceConfig] =
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "create") {
      findByUniqueFields(resource).flatMap {
        case Some(existing) => Future.successful(existing)
        case None =>
          db.run(table += resource).map(_ => resource)
      }
    }

  override def update(resource: ResourceConfig): Future[Option[ResourceConfig]] =
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "update") {
      db.run(table.filter(_.id === resource.getId).update(resource)).map {
        case 0 => None
        case _ => Some(resource)
      }
    }

  override def delete(id: String): Future[Boolean] =
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "delete") {
      db.run(table.filter(_.id === id).delete).map(_ > 0)
    }
}
