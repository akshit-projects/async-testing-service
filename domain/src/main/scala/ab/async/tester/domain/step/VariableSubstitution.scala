package ab.async.tester.domain.step

import io.circe.Json
import io.circe.parser._

import scala.util.Try
import scala.util.matching.Regex

/**
 * Utility for handling variable substitution in step inputs
 * Supports syntax like ${stepName.responseField.subField}
 */
object VariableSubstitution {
  
  // Regex to match variable references like ${stepName.field.subField}
  private val variablePattern: Regex = """\$\{([^}]+)\}""".r
  
  /**
   * Extract all variable references from a string
   * @param input The input string that may contain variable references
   * @return List of variable references found
   */
  def extractVariableReferences(input: String): List[VariableReference] = {
    variablePattern.findAllMatchIn(input).map { m =>
      val fullExpression = m.group(1)
      parseVariableReference(fullExpression)
    }.toList
  }
  
  /**
   * Parse a variable reference expression like "stepName.responseField.subField"
   * @param expression The variable expression
   * @return VariableReference object
   */
  private def parseVariableReference(expression: String): VariableReference = {
    val parts = expression.split("\\.")
    if (parts.length < 2) {
      throw new IllegalArgumentException(s"Invalid variable reference: $expression. Must be in format stepName.field[.subField]")
    }
    
    val stepName = parts(0)
    val fieldPath = parts.drop(1).toList
    
    VariableReference(stepName, fieldPath, expression)
  }
  
  /**
   * Substitute variables in a string using step responses
   * @param input The input string containing variable references
   * @param stepResponses Map of step name to step response
   * @return The string with variables substituted
   */
  def substituteVariables(input: String, stepResponses: Map[String, StepResponse]): String = {
    variablePattern.replaceAllIn(input, { m =>
      val expression = m.group(1)
      val varRef = parseVariableReference(expression)
      
      stepResponses.get(varRef.stepName) match {
        case Some(stepResponse) =>
          extractValueFromResponse(stepResponse, varRef.fieldPath) match {
            case Some(value) => Regex.quoteReplacement(value)
            case None => 
              throw new RuntimeException(s"Could not extract value for ${varRef.originalExpression} from step ${varRef.stepName}")
          }
        case None =>
          throw new RuntimeException(s"Step ${varRef.stepName} not found for variable reference ${varRef.originalExpression}")
      }
    })
  }
  
  /**
   * Extract value from step response using field path
   * @param stepResponse The step response
   * @param fieldPath List of field names to navigate
   * @return Optional extracted value as string
   */
  private def extractValueFromResponse(stepResponse: StepResponse, fieldPath: List[String]): Option[String] = {
    stepResponse.response match {
      case httpResponse: HttpResponse =>
        extractFromHttpResponse(httpResponse, fieldPath)
      case kafkaResponse: KafkaMessagesResponse =>
        extractFromKafkaResponse(kafkaResponse, fieldPath)
      case delayResponse: DelayResponse =>
        extractFromDelayResponse(delayResponse, fieldPath)
      case stepError: StepError =>
        extractFromStepError(stepError, fieldPath)
    }
  }
  
  /**
   * Extract value from HTTP response
   */
  private def extractFromHttpResponse(response: HttpResponse, fieldPath: List[String]): Option[String] = {
    fieldPath match {
      case "status" :: Nil => Some(response.status.toString)
      case "response" :: Nil => Some(response.response)
      case "responseHeaders" :: headerName :: Nil => response.headers.get(headerName)
      case "response" :: jsonPath => extractFromJsonString(response.response, jsonPath)
      case _ => None
    }
  }
  
  /**
   * Extract value from Kafka response
   */
  private def extractFromKafkaResponse(response: KafkaMessagesResponse, fieldPath: List[String]): Option[String] = {
    fieldPath match {
      case "messages" :: indexStr :: "key" :: Nil =>
        Try(indexStr.toInt).toOption.flatMap { index =>
          response.messages.lift(index).flatMap(_.key)
        }
      case "messages" :: indexStr :: "value" :: Nil =>
        Try(indexStr.toInt).toOption.flatMap { index =>
          response.messages.lift(index).map(_.value)
        }
      case "messages" :: "count" :: Nil => Some(response.messages.length.toString)
      case _ => None
    }
  }
  
  /**
   * Extract value from Delay response
   */
  private def extractFromDelayResponse(response: DelayResponse, fieldPath: List[String]): Option[String] = {
    fieldPath match {
      case "success" :: Nil => Some(response.success.toString)
      case _ => None
    }
  }
  
  /**
   * Extract value from Step error
   */
  private def extractFromStepError(error: StepError, fieldPath: List[String]): Option[String] = {
    fieldPath match {
      case "error" :: Nil => Some(error.error)
      case "expectedValue" :: Nil => error.expectedValue
      case "actualValue" :: Nil => error.actualValue
      case _ => None
    }
  }
  
  /**
   * Extract value from JSON string using path
   */
  private def extractFromJsonString(jsonString: String, path: List[String]): Option[String] = {
    parse(jsonString) match {
      case Right(json) => extractFromJson(json, path)
      case Left(_) => None
    }
  }
  
  /**
   * Extract value from JSON using path
   */
  private def extractFromJson(json: Json, path: List[String]): Option[String] = {
    path.foldLeft(Option(json)) { (currentJson, field) =>
      currentJson.flatMap { j =>
        if (j.isObject) {
          j.asObject.flatMap(_.apply(field))
        } else if (j.isArray && field.forall(_.isDigit)) {
          Try(field.toInt).toOption.flatMap { index =>
            j.asArray.flatMap(_.lift(index))
          }
        } else {
          None
        }
      }
    }.map(_.toString.stripPrefix("\"").stripSuffix("\""))
  }
  
  /**
   * Validate that all variable references in a flow can be resolved
   * @param steps List of flow steps
   * @return List of validation errors
   */
  def validateVariableReferences(steps: List[FlowStep]): List[String] = {
    val errors = scala.collection.mutable.ListBuffer[String]()
    val availableSteps = scala.collection.mutable.Set[String]()
    
    steps.foreach { step =>
      // Check variable references in this step
      val stepErrors = validateStepVariableReferences(step, availableSteps.toSet)
      errors ++= stepErrors
      
      // Add this step to available steps for subsequent steps
      availableSteps += step.name
    }
    
    errors.toList
  }
  
  /**
   * Validate variable references in a single step
   */
  private def validateStepVariableReferences(step: FlowStep, availableSteps: Set[String]): List[String] = {
    val errors = scala.collection.mutable.ListBuffer[String]()
    
    // Extract all string fields from step meta that might contain variables
    val stringFields = extractStringFieldsFromStepMeta(step.meta)
    
    stringFields.foreach { field =>
      val varRefs = extractVariableReferences(field)
      varRefs.foreach { varRef =>
        if (!availableSteps.contains(varRef.stepName)) {
          errors += s"Step '${step.name}' references undefined step '${varRef.stepName}' in expression '${varRef.originalExpression}'"
        }
      }
    }
    
    errors.toList
  }
  
  /**
   * Extract all string fields from step meta that might contain variable references
   */
  private def extractStringFieldsFromStepMeta(meta: StepMeta): List[String] = {
    meta match {
      case httpMeta: HttpStepMeta =>
        List(
          httpMeta.body,
          httpMeta.expectedStatus,
          httpMeta.expectedResponse
        ).flatten ++ httpMeta.headers.map(_.values).getOrElse(Nil)
        
      case kafkaSubMeta: KafkaSubscribeMeta =>
        List(kafkaSubMeta.topicName, kafkaSubMeta.groupId)
        
      case kafkaPubMeta: KafkaPublishMeta =>
        kafkaPubMeta.topicName :: kafkaPubMeta.messages.flatMap(msg => 
          List(msg.key, Some(msg.value)).flatten
        )
        
      case _: DelayStepMeta =>
        List.empty // DelayStepMeta doesn't have string fields that would contain variables
    }
  }
}

/**
 * Represents a variable reference in a step input
 */
case class VariableReference(
  stepName: String,
  fieldPath: List[String],
  originalExpression: String
)
