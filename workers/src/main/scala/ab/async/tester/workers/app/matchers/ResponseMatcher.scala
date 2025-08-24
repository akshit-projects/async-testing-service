package ab.async.tester.workers.app.matchers

import org.slf4j.LoggerFactory
import play.api.libs.json.{JsArray, JsBoolean, JsNumber, JsObject, JsString, JsValue, Json}

import scala.util.{Success, Try}

object ResponseMatcher {

  private val logger = LoggerFactory.getLogger(this.getClass)

  // Placeholder strings for wildcard matching
  private val ANY_STRING = "any.string"
  private val ANY_NUMBER = "any.number"
  private val ANY_BOOLEAN = "any.boolean"
  private val ANY_OBJECT = "any.object"
  private val ANY_ARRAY = "any.array"
  private val ANY_VALUE = "any.value" // Matches any type
  // Magic key to enable flexible array matching
  private val MAGIC_KEY_ARRAY_SUBSET = "$matchArraySubset$"

  /**
   * Main matching function. Delegates to JSON matching if content is JSON, otherwise does a string comparison.
   */
  def matches(expected: String, actual: String, contentType: Option[String]): Boolean = {
    if (contentType.exists(_.toLowerCase.contains("application/json"))) {
      (Try(Json.parse(expected)), Try(Json.parse(actual))) match {
        case (Success(expectedJson), Success(actualJson)) =>
          var matchArraySubset = false
          var cleanedExpectedJson = expectedJson

          // NEW: Logic to detect the magic key in either a root object OR a root array
          expectedJson match {
            case arr: JsArray =>
              // Case 2: Root is an array
              // Check if any element in the array is an object containing the magic key
              val (newElements, keyFound) = arr.value.foldLeft((Seq.empty[JsValue], false)) {
                case ((acc, found), el) =>
                  // *** CORRECTION HERE ***
                  val keyInElement = el.asOpt[JsObject]
                    .exists(o => (o \ MAGIC_KEY_ARRAY_SUBSET).asOpt[Boolean].getOrElse(false))

                  if (keyInElement) {
                    (acc :+ (el.as[JsObject] - MAGIC_KEY_ARRAY_SUBSET), true)
                  } else {
                    (acc :+ el, found)
                  }
              }
              if (keyFound) {
                matchArraySubset = true
                cleanedExpectedJson = JsArray(newElements)
              }
            case obj: JsObject =>
              // Case 1: Root is an object (original behavior)
              if ((obj \ MAGIC_KEY_ARRAY_SUBSET).asOpt[Boolean].getOrElse(false)) {
                matchArraySubset = true
                cleanedExpectedJson = obj - MAGIC_KEY_ARRAY_SUBSET
              }

            case _ => // Not an object or array, mode remains false
          }

          jsonMatches(cleanedExpectedJson, actualJson, matchArraySubset)
        case _ =>
          logger.warn("Could not parse expected or actual body as JSON. Falling back to string comparison.")
          expected == actual
      }
    } else {
      expected == actual
    }
  }/**
   * Recursively compares two JSON JsValue objects.
   */
  private def jsonMatches(expected: JsValue, actual: JsValue, matchArraySubset: Boolean): Boolean = {
    (expected, actual) match {
      // 1. Wildcard matching
      case (JsString(placeholder), _) =>
        placeholder match {
          case ANY_STRING  => actual.isInstanceOf[JsString]
          case ANY_NUMBER  => actual.isInstanceOf[JsNumber]
          case ANY_BOOLEAN => actual.isInstanceOf[JsBoolean]
          case ANY_OBJECT  => actual.isInstanceOf[JsObject]
          case ANY_ARRAY   => actual.isInstanceOf[JsArray]
          case ANY_VALUE   => true
          case _           => expected == actual
        }

      // 2. Recursive matching for objects (always partial)
      case (expectedObj: JsObject, actualObj: JsObject) =>
        val areKeysMatching = expectedObj.keys.forall { key =>
          actualObj.keys.contains(key) && jsonMatches(expectedObj(key), actualObj(key), matchArraySubset)
        }
        (matchArraySubset || expectedObj.keys.size == actualObj.keys.size) && areKeysMatching

      // 3. Recursive matching for arrays (mode-dependent)
      case (JsArray(expectedElements), JsArray(actualElements)) =>
        if (matchArraySubset) {
          // SUBSET MODE: Every expected element must be found somewhere in the actual array. Order and extra elements are ignored.
          expectedElements.forall { expectedElem =>
            actualElements.exists { actualElem =>
              jsonMatches(expectedElem, actualElem, matchArraySubset)
            }
          }
        } else {
          // STRICT MODE (default): Arrays must have the same length and elements must match in order.
          expectedElements.length == actualElements.length &&
            expectedElements.zip(actualElements).forall { case (e, a) => jsonMatches(e, a, matchArraySubset) }
        }

      // 4. Direct comparison for other value types
      case _ => expected == actual
    }
  }

}
