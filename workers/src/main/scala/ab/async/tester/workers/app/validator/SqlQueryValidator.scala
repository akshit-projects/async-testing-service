package ab.async.tester.workers.app.validator

import com.typesafe.config.Config
import javax.inject.{Inject, Singleton}
import scala.util.matching.Regex
import scala.jdk.CollectionConverters._

/**
 * Validator for SQL queries to ensure only safe SELECT operations are allowed
 */
@Singleton
class SqlQueryValidator @Inject()(config: Config) {

  // Load configuration
  private val maxRows = config.getInt("sql.security.maxRows")
  private val allowedTables = config.getStringList("sql.security.allowedTables").asScala.toSet
  private val enforceTableRestrictions = config.getBoolean("sql.security.enforceTableRestrictions")
  
  // Regex patterns for validation
  private val selectPattern: Regex = """^\s*SELECT\s+""".r.unanchored
  private val dangerousPatterns: List[Regex] = List(
    """(?i)\b(INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|EXEC|EXECUTE)\b""".r,
    """(?i)\b(GRANT|REVOKE|COMMIT|ROLLBACK|SAVEPOINT)\b""".r,
    """(?i)\b(CALL|PROCEDURE|FUNCTION)\b""".r,
    """(?i)--""".r,  // SQL comments
    """/\*.*?\*/""".r,  // Multi-line comments
    """(?i)\bxp_cmdshell\b""".r,  // SQL Server command execution
    """(?i)\bsp_executesql\b""".r,  // SQL Server dynamic SQL
    """(?i)\bdbms_\w+""".r,  // Oracle DBMS packages
    """(?i)\butl_\w+""".r,  // Oracle UTL packages
    """(?i)\bpg_\w+""".r,  // PostgreSQL system functions (some can be dangerous)
    """(?i)\bcopy\s+""".r,  // PostgreSQL COPY command
    """(?i)\\\w+""".r  // PostgreSQL meta-commands
  )
  
  // Allowed functions and keywords
  private val allowedFunctions: Set[String] = Set(
    "COUNT", "SUM", "AVG", "MIN", "MAX", "UPPER", "LOWER", "TRIM", "LENGTH",
    "SUBSTRING", "CONCAT", "COALESCE", "CASE", "WHEN", "THEN", "ELSE", "END",
    "CAST", "CONVERT", "DATE", "TIME", "TIMESTAMP", "NOW", "CURRENT_DATE",
    "CURRENT_TIME", "CURRENT_TIMESTAMP", "EXTRACT", "DATE_PART", "TO_CHAR",
    "TO_DATE", "ABS", "ROUND", "CEIL", "FLOOR", "MOD", "POWER", "SQRT"
  )
  
  /**
   * Validates if a SQL query is safe to execute
   * @param query The SQL query to validate
   * @return ValidationResult indicating if the query is valid
   */
  def validateQuery(query: String): ValidationResult = {
    val trimmedQuery = query.trim
    
    if (trimmedQuery.isEmpty) {
      return ValidationResult(isValid = false, "Query cannot be empty")
    }
    
    // Check if it's a SELECT statement
    if (selectPattern.findFirstIn(trimmedQuery.toUpperCase).isEmpty) {
      return ValidationResult(isValid = false, "Only SELECT statements are allowed")
    }
    
    // Check for dangerous patterns
    for (pattern <- dangerousPatterns) {
      pattern.findFirstIn(trimmedQuery) match {
        case Some(match_) =>
          return ValidationResult(isValid = false, s"Dangerous SQL pattern detected: $match_")
        case None => // Continue checking
      }
    }
    
    // Check for multiple statements (semicolon separation)
    val statements = trimmedQuery.split(";").map(_.trim).filter(_.nonEmpty)
    if (statements.length > 1) {
      return ValidationResult(isValid = false, "Multiple statements are not allowed")
    }

    // Validate table access restrictions
    if (enforceTableRestrictions) {
      val tableValidation = validateTableAccess(trimmedQuery)
      if (!tableValidation.isValid) {
        return tableValidation
      }
    }

    // Add LIMIT clause if not present
    val queryWithLimit = addLimitClause(trimmedQuery)

    // Additional checks for nested queries and subqueries
    if (!validateNestedQueries(queryWithLimit)) {
      return ValidationResult(isValid = false, "Complex nested queries or subqueries with potential security risks detected")
    }

    ValidationResult(isValid = true, "Query is valid", Some(queryWithLimit))
  }
  
  /**
   * Validates nested queries and subqueries
   */
  private def validateNestedQueries(query: String): Boolean = {
    val upperQuery = query.toUpperCase
    
    // Count parentheses to detect deeply nested queries
    val openParens = upperQuery.count(_ == '(')
    val closeParens = upperQuery.count(_ == ')')
    
    if (openParens != closeParens) {
      return false // Unbalanced parentheses
    }
    
    if (openParens > 5) {
      return false // Too many nested levels
    }
    
    // Check for UNION operations (can be used for injection)
    if (upperQuery.contains("UNION")) {
      return false
    }
    
    true
  }
  

  
  /**
   * Validates parameter names to ensure they follow safe naming conventions
   */
  def validateParameterNames(parameters: Map[String, String]): ValidationResult = {
    val paramNamePattern = """^[a-zA-Z_][a-zA-Z0-9_]*$""".r
    
    for ((paramName, _) <- parameters) {
      if (!paramNamePattern.matches(paramName)) {
        return ValidationResult(isValid = false, s"Invalid parameter name: $paramName. Parameter names must start with a letter or underscore and contain only alphanumeric characters and underscores.")
      }
    }
    
    ValidationResult(isValid = true, "All parameter names are valid")
  }
  


  /**
   * Validates that the query only accesses allowed tables
   */
  private def validateTableAccess(query: String): ValidationResult = {
    val upperQuery = query.toUpperCase

    // Extract table names from FROM and JOIN clauses
    val fromPattern = """FROM\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\s*,\s*[a-zA-Z_][a-zA-Z0-9_]*)*)""".r
    val joinPattern = """JOIN\s+([a-zA-Z_][a-zA-Z0-9_]*)""".r

    val fromTables = fromPattern.findAllMatchIn(upperQuery).flatMap { m =>
      m.group(1).split(",").map(_.trim.split("\\s+").head) // Get table name before alias
    }.toSet

    val joinTables = joinPattern.findAllMatchIn(upperQuery).map(_.group(1)).toSet

    val allTables = fromTables ++ joinTables
    val upperAllowedTables = allowedTables.map(_.toUpperCase)

    val unauthorizedTables = allTables.filterNot(upperAllowedTables.contains)

    if (unauthorizedTables.nonEmpty) {
      ValidationResult(isValid = false, s"Access to tables not allowed: ${unauthorizedTables.mkString(", ")}. Allowed tables: ${allowedTables.mkString(", ")}")
    } else {
      ValidationResult(isValid = true, "Table access validation passed")
    }
  }

  /**
   * Adds LIMIT clause to query if not present
   */
  private def addLimitClause(query: String): String = {
    val upperQuery = query.toUpperCase

    // Check if LIMIT clause already exists
    if (upperQuery.contains("LIMIT")) {
      // Extract existing limit and ensure it doesn't exceed maxRows
      val limitPattern = """LIMIT\s+(\d+)""".r
      limitPattern.findFirstMatchIn(upperQuery) match {
        case Some(m) =>
          val existingLimit = m.group(1).toInt
          if (existingLimit > maxRows) {
            // Replace with maxRows
            query.replaceAll("(?i)LIMIT\\s+\\d+", s"LIMIT $maxRows")
          } else {
            query
          }
        case None => query
      }
    } else {
      // Add LIMIT clause
      s"$query LIMIT $maxRows"
    }
  }
}

object SqlQueryValidator {
  /**
   * Sanitizes query parameters to prevent injection (static method for backward compatibility)
   */
  def sanitizeParameters(parameters: Map[String, String]): Map[String, String] = {
    parameters.map { case (key, value) =>
      key -> sanitizeParameterValue(value)
    }
  }

  /**
   * Sanitizes a single parameter value
   */
  private def sanitizeParameterValue(value: String): String = {
    // Remove potentially dangerous characters and patterns
    value
      .replaceAll("['\"`;]", "") // Remove quotes and semicolons
      .replaceAll("--.*", "") // Remove SQL comments
      .replaceAll("/\\*.*?\\*/", "") // Remove multi-line comments
      .trim
  }

  /**
   * Replaces parameters in the query with placeholders for prepared statements
   */
  def replaceParametersWithPlaceholders(query: String, parameters: Map[String, String]): String = {
    var processedQuery = query

    for ((paramName, _) <- parameters) {
      // Replace ${paramName} with ? for prepared statements
      processedQuery = processedQuery.replaceAll(s"\\$$\\{$paramName\\}", "?")
    }

    processedQuery
  }

  /**
   * Gets parameter values in the order they appear in the query
   */
  def getParameterValuesInOrder(query: String, parameters: Map[String, String]): List[String] = {
    val paramPattern = """\$\{([^}]+)\}""".r
    val matches = paramPattern.findAllMatchIn(query).toList

    matches.map(_.group(1)).map(paramName =>
      parameters.getOrElse(paramName, throw new IllegalArgumentException(s"Parameter $paramName not found"))
    )
  }
}

/**
 * Result of SQL query validation
 */
case class ValidationResult(isValid: Boolean, message: String, processedQuery: Option[String] = None)
