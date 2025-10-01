package ab.async.tester.workers.app.runner

import ab.async.tester.domain.execution.ExecutionStep
import ab.async.tester.domain.resource.SQLDBConfig
import ab.async.tester.domain.step.{SqlResponse, SqlStepMeta, StepResponse}
import ab.async.tester.library.repository.resource.ResourceRepository
import ab.async.tester.library.substitution.VariableSubstitutionService
import ab.async.tester.workers.app.validator.SqlQueryValidator
import com.google.inject.{Inject, Singleton}

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}
import scala.concurrent.{ExecutionContext, Future}

/**
 * SQL step runner for executing SELECT queries
 */
@Singleton
class SqlStepRunner @Inject()(
  resourceRepository: ResourceRepository,
  sqlQueryValidator: SqlQueryValidator,
  protected val variableSubstitutionService: VariableSubstitutionService
)(implicit ec: ExecutionContext) extends BaseStepRunner {
  
  override protected val runnerName: String = "SqlStepRunner"

  override def executeStep(step: ExecutionStep, previousResults: List[StepResponse]): Future[StepResponse] = {
    step.meta match {
      case sqlMeta: SqlStepMeta =>
        executeSqlQuery(step, sqlMeta)
      case _ =>
        Future.successful(createErrorResponse(step, "Invalid step meta type for SQL runner"))
    }
  }

  private def executeSqlQuery(step: ExecutionStep, sqlMeta: SqlStepMeta): Future[StepResponse] = {
    logger.info(s"Executing SQL query for step ${step.name}")

    // Validate the query first
    val validationResult = sqlQueryValidator.validateQuery(sqlMeta.query)
    if (!validationResult.isValid) {
      return Future.successful(createErrorResponse(step, s"SQL validation failed: ${validationResult.message}"))
    }

    // Get the processed query with LIMIT clause
    val processedQuery = validationResult.processedQuery.getOrElse(sqlMeta.query)

    // Validate parameters if present
    sqlMeta.parameters.foreach { params =>
      val paramValidation = sqlQueryValidator.validateParameterNames(params)
      if (!paramValidation.isValid) {
        return Future.successful(createErrorResponse(step, s"Parameter validation failed: ${paramValidation.message}"))
      }
    }

    // Get database resource
    resourceRepository.findById(sqlMeta.resourceId).flatMap {
      case Some(resource: SQLDBConfig) =>
        executeQueryWithResource(step, sqlMeta, resource, processedQuery)
      case None =>
        Future.successful(createErrorResponse(step, s"Database resource not found: ${sqlMeta.resourceId}"))
    }
  }

  private def executeQueryWithResource(step: ExecutionStep, sqlMeta: SqlStepMeta, resource: SQLDBConfig, processedQuery: String): Future[StepResponse] = {
    Future {
      val startTime = System.currentTimeMillis()
      
      try {
        // Extract connection details from resource
        val host = resource.dbUrl
        val port = resource.port
        val database = resource.database
        val username = resource.username
        val password = resource.password.getOrElse("")
        
        val jdbcUrl = s"jdbc:postgresql://$host:$port/$database"
        
        // Establish connection
        val connection: Connection = DriverManager.getConnection(jdbcUrl, username, password)
        
        try {
          // Prepare query with parameters
          val (finalQuery, paramValues) = sqlMeta.parameters match {
            case Some(params) =>
              val sanitizedParams = SqlQueryValidator.sanitizeParameters(params)
              val queryWithPlaceholders = SqlQueryValidator.replaceParametersWithPlaceholders(processedQuery, sanitizedParams)
              val orderedValues = SqlQueryValidator.getParameterValuesInOrder(processedQuery, sanitizedParams)
              (queryWithPlaceholders, orderedValues)
            case None =>
              (processedQuery, List.empty[String])
          }
          
          logger.info(s"Executing query: $finalQuery")

          // Execute query
          val preparedStatement: PreparedStatement = connection.prepareStatement(finalQuery)
          
          // Set parameters
          paramValues.zipWithIndex.foreach { case (value, index) =>
            preparedStatement.setString(index + 1, value)
          }
          
          val resultSet: ResultSet = preparedStatement.executeQuery()
          
          // Process results
          val (rows, columns) = processResultSet(resultSet)
          val executionTime = System.currentTimeMillis() - startTime
          
          // Validate results against expectations
          val validationError = validateResults(sqlMeta, rows, columns)
          if (validationError.isDefined) {
            createErrorResponse(step, validationError.get)
          } else {
            createSuccessResponse(step, SqlResponse(rows, rows.length, columns, executionTime))
          }
          
        } finally {
          connection.close()
        }
        
      } catch {
        case e: Exception =>
          logger.error(s"SQL execution failed for step ${step.name}: ${e.getMessage}", e)
          createErrorResponse(step, s"SQL execution failed: ${e.getMessage}")
      }
    }
  }

  private def processResultSet(resultSet: ResultSet): (List[Map[String, String]], List[String]) = {
    val metaData = resultSet.getMetaData
    val columnCount = metaData.getColumnCount
    val columns = (1 to columnCount).map(metaData.getColumnName).toList
    
    val rows = scala.collection.mutable.ListBuffer[Map[String, String]]()
    
    while (resultSet.next()) {
      val row = columns.map { column =>
        val value = Option(resultSet.getString(column)).getOrElse("")
        column -> value
      }.toMap
      rows += row
    }
    
    (rows.toList, columns)
  }

  private def validateResults(sqlMeta: SqlStepMeta, rows: List[Map[String, String]], columns: List[String]): Option[String] = {
    // Validate row count if expected
    sqlMeta.expectedRowCount.foreach { expectedCount =>
      if (rows.length != expectedCount) {
        return Some(s"Expected $expectedCount rows, but got ${rows.length}")
      }
    }
    
    // Validate columns if expected
    sqlMeta.expectedColumns.foreach { expectedColumns =>
      val missingColumns = expectedColumns.filterNot(columns.contains)
      if (missingColumns.nonEmpty) {
        return Some(s"Missing expected columns: ${missingColumns.mkString(", ")}")
      }
    }
    
    None // No validation errors
  }
}
