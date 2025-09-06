package ab.async.tester.library.repository.organisation

import ab.async.tester.domain.organisation.Organisation
import com.google.inject.{ImplementedBy, Inject, Singleton}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{ProvenShape, Tag}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[OrganisationRepositoryImpl])
trait OrganisationRepository {
  def insert(organisation: Organisation): Future[Organisation]
  def findById(id: String): Future[Option[Organisation]]
  def findAllWithCount(search: Option[String] = None, limit: Int = 50, page: Int = 0): Future[(List[Organisation], Long)]
  def update(organisation: Organisation): Future[Boolean]
  def delete(id: String): Future[Boolean]
  def findByName(name: String): Future[Option[Organisation]]
}

@Singleton
class OrganisationRepositoryImpl @Inject()(database: Database)(implicit ec: ExecutionContext) extends OrganisationRepository {

  class OrganisationTable(tag: Tag) extends Table[Organisation](tag, "organisations") {
    def id = column[Option[String]]("id", O.PrimaryKey)
    def name = column[String]("name")
    def createdAt = column[Long]("created_at")
    def modifiedAt = column[Long]("modified_at")

    def * : ProvenShape[Organisation] = (id, name, createdAt, modifiedAt) <> ((Organisation.apply _).tupled, Organisation.unapply)
  }

  private val organisations = TableQuery[OrganisationTable]

  override def insert(organisation: Organisation): Future[Organisation] = {
    val id = java.util.UUID.randomUUID().toString
    val orgWithId = organisation.copy(id = Some(id))
    
    database.run(organisations += orgWithId).map(_ => orgWithId)
  }

  override def findById(id: String): Future[Option[Organisation]] = {
    database.run(organisations.filter(_.id === Option(id)).result.headOption)
  }

  private def buildOrganisationQuery(search: Option[String]) = {
    search match {
      case Some(searchTerm) =>
        organisations.filter(_.name.toLowerCase like s"%${searchTerm.toLowerCase}%")
      case None =>
        organisations
    }
  }

  override def findAllWithCount(search: Option[String], limit: Int, page: Int): Future[(List[Organisation], Long)] = {
    val query = buildOrganisationQuery(search)

    val countQuery = query.length
    val dataQuery = query.sortBy(_.createdAt.desc).drop(page * limit).take(limit)

    // Execute both queries in parallel for better performance
    val countFuture = database.run(countQuery.result)
    val dataFuture = database.run(dataQuery.result)

    for {
      count <- countFuture
      data <- dataFuture
    } yield (data.toList, count.toLong)
  }

  override def update(organisation: Organisation): Future[Boolean] = {
    organisation.id match {
      case Some(id) =>
        val updatedOrg = organisation.copy(modifiedAt = System.currentTimeMillis())
        database.run(
          organisations
            .filter(_.id === Option(id))
            .update(updatedOrg)
        ).map(_ > 0)
      case None =>
        Future.successful(false)
    }
  }

  override def delete(id: String): Future[Boolean] = {
    database.run(organisations.filter(_.id === Option(id)).delete).map(_ > 0)
  }

  override def findByName(name: String): Future[Option[Organisation]] = {
    database.run(organisations.filter(_.name === name).result.headOption)
  }
}
