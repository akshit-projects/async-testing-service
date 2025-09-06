package ab.async.tester.service.organisation

import ab.async.tester.domain.common.{PaginatedResponse, PaginationMetadata}
import ab.async.tester.domain.organisation.Organisation
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.library.repository.organisation.OrganisationRepository
import ab.async.tester.library.utils.MetricUtils
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[OrganisationServiceImpl])
trait OrganisationService {
  def createOrganisation(organisation: Organisation): Future[Organisation]
  def getOrganisation(id: String): Future[Option[Organisation]]
  def getOrganisations(search: Option[String], limit: Int, page: Int): Future[PaginatedResponse[Organisation]]
  def updateOrganisation(organisation: Organisation): Future[Boolean]
  def deleteOrganisation(id: String): Future[Boolean]
}

@Singleton
class OrganisationServiceImpl @Inject()(
  organisationRepository: OrganisationRepository
)(implicit ec: ExecutionContext) extends OrganisationService {

  private implicit val logger: Logger = Logger(this.getClass)
  private val serviceName = "OrganisationService"

  override def createOrganisation(organisation: Organisation): Future[Organisation] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "createOrganisation") {
      validateOrganisation(organisation).flatMap { _ =>
        // Check if organisation name already exists
        organisationRepository.findByName(organisation.name).flatMap {
          case Some(_) =>
            Future.failed(ValidationException(s"Organisation with name '${organisation.name}' already exists"))
          case None =>
            val now = System.currentTimeMillis() / 1000
            val newOrg = organisation.copy(createdAt = now, modifiedAt = now)
            organisationRepository.insert(newOrg)
        }
      }
    }

  override def getOrganisation(id: String): Future[Option[Organisation]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getOrganisation") {
      organisationRepository.findById(id).recover {
        case e: Exception =>
          logger.error(s"Error retrieving organisation $id: ${e.getMessage}", e)
          None
      }
    }

  override def getOrganisations(search: Option[String], limit: Int, page: Int): Future[PaginatedResponse[Organisation]] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "getOrganisations") {
      organisationRepository.findAllWithCount(search, limit, page).map { case (organisations, total) =>
        PaginatedResponse(
          data = organisations,
          pagination = PaginationMetadata(page, limit, total)
        )
      }.recover {
        case e: Exception =>
          logger.error(s"Error retrieving organisations: ${e.getMessage}", e)
          PaginatedResponse(Nil, PaginationMetadata(page, limit, 0))
      }
    }

  override def updateOrganisation(organisation: Organisation): Future[Boolean] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "updateOrganisation") {
      validateOrganisation(organisation).flatMap { _ =>
        organisation.id match {
          case Some(id) =>
            // Check if another organisation with the same name exists (excluding current one)
            organisationRepository.findByName(organisation.name).flatMap {
              case Some(existing) if existing.id != organisation.id =>
                Future.failed(ValidationException(s"Organisation with name '${organisation.name}' already exists"))
              case _ =>
                organisationRepository.update(organisation)
            }
          case None =>
            Future.failed(ValidationException("Organisation ID is required for update"))
        }
      }
    }

  override def deleteOrganisation(id: String): Future[Boolean] =
    MetricUtils.withAsyncServiceMetrics(serviceName, "deleteOrganisation") {
      organisationRepository.delete(id).recover {
        case e: Exception =>
          logger.error(s"Error deleting organisation $id: ${e.getMessage}", e)
          false
      }
    }

  private def validateOrganisation(organisation: Organisation): Future[Unit] = {
    if (organisation.name.trim.isEmpty) {
      Future.failed(ValidationException("Organisation name cannot be empty"))
    } else if (organisation.name.length > 255) {
      Future.failed(ValidationException("Organisation name cannot exceed 255 characters"))
    } else {
      Future.successful(())
    }
  }
}
