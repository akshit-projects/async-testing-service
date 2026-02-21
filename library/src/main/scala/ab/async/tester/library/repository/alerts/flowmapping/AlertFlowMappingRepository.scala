package ab.async.tester.library.repository.alerts.flowmapping

import ab.async.tester.domain.alert.{AlertFlowMapping, AlertTriggerMeta, ReportingConfig}
import ab.async.tester.domain.enums.AlertProvider
import ab.async.tester.library.utils.{DecodingUtils, MetricUtils}
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Logger
import ab.async.tester.library.driver.MyPostgresProfile.api._
import io.circe.syntax.EncoderOps

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AlertFlowMappingImpl])
trait AlertFlowMappingRepository {
  def findById(id: String): Future[Option[AlertFlowMapping]]
  def findByAlertProvider(alertProvider: AlertProvider): Future[List[AlertFlowMapping]]
  def findAll(orgId: Option[String], teamId: Option[String]): Future[List[AlertFlowMapping]]
  def insert(mapping: AlertFlowMapping): Future[AlertFlowMapping]
  def update(mapping: AlertFlowMapping): Future[Boolean]
  def delete(id: String): Future[Boolean]
}

@Singleton
class AlertFlowMappingImpl @Inject()(
    db: Database
)(implicit ec: ExecutionContext)
    extends AlertFlowMappingRepository {

  private implicit val logger: Logger = Logger(this.getClass)
  private val repositoryName = "OpsgenieMappingRepository"

  class AlertFlowMappingTable(tag: Tag)
      extends Table[AlertFlowMapping](tag, "alert_flow_mappings") {
    def id = column[String]("id", O.PrimaryKey)
    def provider = column[String]("provider")
    def alertTriggerMeta = column[String]("trigger_meta")
    def flowId = column[String]("flow_id")
    def reportingConfig = column[Option[String]]("reporting_config")
    def orgId = column[Option[String]]("org_id")
    def teamId = column[Option[String]]("team_id")
    def isActive = column[Boolean]("is_active")
    def createdAt = column[Long]("created_at")
    def updatedAt = column[Long]("updated_at")

    def * = (
      id.?,
      provider,
      alertTriggerMeta,
      flowId,
      reportingConfig,
      orgId,
      teamId,
      isActive,
      createdAt,
      updatedAt
    ) <> (
      {
        case (
              id,
              provider,
              alertMessagePattern,
              flowId,
              reportingConfig,
              orgId,
              teamId,
              isActive,
              createdAt,
              updatedAt
            ) =>
          AlertFlowMapping(
            id,
            AlertProvider.fromString(provider),
            DecodingUtils.decodeWithErrorLogs[AlertTriggerMeta](alertMessagePattern),
            flowId,
            reportingConfig.map(DecodingUtils.decodeWithErrorLogs[ReportingConfig](_)),
            orgId,
            teamId,
            isActive,
            createdAt,
            updatedAt
          )
      },
      (m: AlertFlowMapping) => {
        Some(
          (
            m.id,
            m.provider.toString,
            m.triggerMeta.asJson.noSpaces,
            m.flowId,
            Option(m.reportingConfig.asJson.noSpaces),
            m.orgId,
            m.teamId,
            m.isActive,
            m.createdAt,
            m.updatedAt
          )
        )
      }
    )
  }

  private val mappings = TableQuery[AlertFlowMappingTable]

  override def findById(id: String): Future[Option[AlertFlowMapping]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findById") {
      db.run(mappings.filter(_.id === id).result.headOption)
    }
  }

  override def findByAlertProvider(
      alertProvider: AlertProvider,
  ): Future[List[AlertFlowMapping]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findByTag") {
      val provider = alertProvider.toString
      db.run(
        mappings
          .filter(m =>
            m.provider === provider && m.isActive === true
          )
          .result
      ).map(_.toList)
    }
  }

  override def findAll(
      orgId: Option[String],
      teamId: Option[String]
  ): Future[List[AlertFlowMapping]] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "findAll") {
      val query = teamId match {
        case Some(tid) =>
          mappings.filter(m => m.orgId === orgId && m.teamId === tid)
        case None => mappings.filter(_.orgId === orgId)
      }
      db.run(query.result).map(_.toList)
    }
  }

  override def insert(
      mapping: AlertFlowMapping
  ): Future[AlertFlowMapping] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "insert") {
      val mappingWithId = mapping.copy(id = Option(UUID.randomUUID().toString))
      db.run(mappings += mappingWithId).map(_ => mappingWithId)
    }
  }

  override def update(mapping: AlertFlowMapping): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "update") {
      mapping.id match {
        case Some(mid) =>
          db.run(mappings.filter(_.id === mid).update(mapping)).map(_ > 0)
        case None =>
          Future.successful(false)
      }
    }
  }

  override def delete(id: String): Future[Boolean] = {
    MetricUtils.withAsyncRepositoryMetrics(repositoryName, "delete") {
      db.run(mappings.filter(_.id === id).delete).map(_ > 0)
    }
  }
}
