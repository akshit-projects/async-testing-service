package ab.async.tester.library.repository.team

import ab.async.tester.domain.team.Team
import com.google.inject.{ImplementedBy, Inject, Singleton}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{ProvenShape, Tag}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TeamRepositoryImpl])
trait TeamRepository {
  def insert(team: Team): Future[Team]
  def findById(id: String): Future[Option[Team]]
  def findAllWithCount(search: Option[String] = None, orgId: Option[String] = None, limit: Int = 50, page: Int = 0): Future[(List[Team], Long)]
  def findByOrganisation(orgId: String): Future[List[Team]]
  def update(team: Team): Future[Boolean]
  def delete(id: String): Future[Boolean]
  def findByNameAndOrg(name: String, orgId: String): Future[Option[Team]]
}

@Singleton
class TeamRepositoryImpl @Inject()(database: Database)(implicit ec: ExecutionContext) extends TeamRepository {

  class TeamTable(tag: Tag) extends Table[Team](tag, "teams") {
    def id = column[Option[String]]("id", O.PrimaryKey)
    def orgId = column[String]("org_id")
    def name = column[String]("name")
    def createdAt = column[Long]("created_at")
    def modifiedAt = column[Long]("modified_at")

    def * : ProvenShape[Team] = (id, orgId, name, createdAt, modifiedAt) <> ((Team.apply _).tupled, Team.unapply)
  }

  private val teams = TableQuery[TeamTable]

  override def insert(team: Team): Future[Team] = {
    val id = java.util.UUID.randomUUID().toString
    val teamWithId = team.copy(id = Some(id))
    
    database.run(teams += teamWithId).map(_ => teamWithId)
  }

  override def findById(id: String): Future[Option[Team]] = {
    database.run(teams.filter(_.id === Option(id)).result.headOption)
  }

  private def buildTeamQuery(search: Option[String], orgId: Option[String]) = {
    var query = teams.asInstanceOf[Query[TeamTable, Team, Seq]]

    // Apply search filter
    search.foreach { searchTerm =>
      query = query.filter(_.name.toLowerCase like s"%${searchTerm.toLowerCase}%")
    }

    // Apply organisation filter
    orgId.foreach { orgIdValue =>
      query = query.filter(_.orgId === orgIdValue)
    }

    query
  }

  override def findAllWithCount(search: Option[String], orgId: Option[String], limit: Int, page: Int): Future[(List[Team], Long)] = {
    val query = buildTeamQuery(search, orgId)

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

  override def findByOrganisation(orgId: String): Future[List[Team]] = {
    database.run(teams.filter(_.orgId === orgId).sortBy(_.name).result).map(_.toList)
  }

  override def update(team: Team): Future[Boolean] = {
    team.id match {
      case Some(id) =>
        val updatedTeam = team.copy(modifiedAt = System.currentTimeMillis())
        database.run(
          teams
            .filter(_.id === Option(id))
            .update(updatedTeam)
        ).map(_ > 0)
      case None =>
        Future.successful(false)
    }
  }

  override def delete(id: String): Future[Boolean] = {
    database.run(teams.filter(_.id === Option(id)).delete).map(_ > 0)
  }

  override def findByNameAndOrg(name: String, orgId: String): Future[Option[Team]] = {
    database.run(teams.filter(t => t.name === name && t.orgId === orgId).result.headOption)
  }
}
