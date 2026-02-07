# Step Creation Guide

## Overview

This guide shows you how to add a new step type to the Async Testing Service. Thanks to the centralized step registry pattern, adding a new step requires updating only **3 key locations**.

## Quick Checklist

When adding a new step type, you need to:

1. ✅ Define the step meta class
2. ✅ Create the step runner implementation  
3. ✅ Register the step type in `StepTypeRegistry`

That's it! The system handles the rest automatically.

---

## Detailed Steps

### 1. Define Step Meta Class

Create a new file in `domain/src/main/scala/ab/async/tester/domain/step/` for your step metadata.

**Template:**

```scala
package ab.async.tester.domain.step

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Step metadata for [YourStep] 
  * @param resourceId External resource ID (if applicable)
  * @param [otherFields] Step-specific configuration
  */
case class YourStepMeta(
    resourceId: String,        // Only if step uses external resources
    field1: String,
    field2: Option[Int] = None,
    // ... other fields
) extends StepMetaWithResource  // Use this trait if step uses resources

object YourStepMeta {
  implicit val encoder: Encoder[YourStepMeta] = deriveEncoder[YourStepMeta]
  implicit val decoder: Decoder[YourStepMeta] = deriveDecoder[YourStepMeta]
}
```

**Key Points:**
- Extend `StepMetaWithResource` if your step needs access to external resources (APIs, databases, etc.)
- Extend `StepMeta` directly if your step doesn't use resources (like `DelayStepMeta`)
- Define encoder/decoder in the companion object for JSON serialization

**Example (HTTP Step):**

```scala
case class HttpStepMeta(
    resourceId: String,
    body: Option[String] = None,
    headers: Option[Map[String, String]] = None,
    expectedStatus: Option[String] = None,
    expectedResponse: Option[String] = None
) extends StepMetaWithResource
```

---

### 2. Create Step Runner

Create a new file in `workers/src/main/scala/ab/async/tester/workers/app/runner/` for your step runner.

**Template:**

```scala
package ab.async.tester.workers.app.runner

import ab.async.tester.domain.enums.StepStatus
import ab.async.tester.domain.execution.ExecutionStep
import ab.async.tester.domain.step.{YourStepMeta, StepError, StepResponse, YourStepResponse}
import ab.async.tester.library.repository.resource.ResourceRepository
import ab.async.tester.library.substitution.VariableSubstitutionService
import com.google.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class YourStepRunner @Inject()(
  // Inject dependencies you need (e.g., repositories, clients)
  resourceRepository: ResourceRepository,
  protected val variableSubstitutionService: VariableSubstitutionService
)(implicit ec: ExecutionContext) extends BaseStepRunner {
  
  override protected val runnerName: String = "YourStepRunner"

  override protected def executeStep(
      step: ExecutionStep,
      previousResults: List[StepResponse]
  ): Future[StepResponse] = {
    try {
      // 1. Extract and validate step metadata
      val meta = step.meta match {
        case m: YourStepMeta => m
        case _ =>
          throw new Exception(s"Invalid metadata for step: ${step.name}")
      }

      // 2.Perform the step logic
      // ... your implementation here ...

      // 3. Return success response
      Future.successful(
        createSuccessResponse(step, YourStepResponse(/* result data */))
      )
    } catch {
      case e: Exception =>
        logger.error(s"Error in step ${step.name}: ${e.getMessage}", e)
        Future.failed(e)
    }
  }
}
```

**Key Points:**
- Extend `BaseStepRunner` which provides common functionality (variable substitution, timeout handling, error handling)
- Inject `VariableSubstitutionService` as a protected val (required by BaseStepRunner)
- Override `runnerName` for logging/metrics
- Override `executeStep` with your step logic
- Use `createSuccessResponse` or `createErrorResponse` helper methods

**Example (HTTP Step Runner):**

```scala
@Singleton
class HttpStepRunner @Inject()(
  wsClient: StandaloneWSClient,
  resourceRepository: ResourceRepository,
  protected val variableSubstitutionService: VariableSubstitutionService
)(implicit ec: ExecutionContext) extends BaseStepRunner {
  override protected val runnerName: String = "HttpStepRunner"

  override protected def executeStep(
      step: ExecutionStep,
      previousResults: List[StepResponse]
  ): Future[StepResponse] = {
    val httpMeta = step.meta.asInstanceOf[HttpStepMeta]
    
    resourceRepository.findById(httpMeta.resourceId).flatMap { resource =>
      val apiConfig = resource.get.asInstanceOf[APISchemaConfig]
      val request = wsClient.url(apiConfig.url)
      
      request.get().map { response =>
        createSuccessResponse(
          step,
          HttpResponse(response.status, response.body, response.headers)
        )
      }
    }
  }
}
```

---

### 3. Register in StepTypeRegistry

Add an entry to the registry in `domain/src/main/scala/ab/async/tester/domain/step/StepTypeRegistry.scala`.

**Steps:**

1. Add a new case object to `StepType` enum in `domain/src/main/scala/ab/async/tester/domain/enums/StepType.scala`:

```scala
sealed trait StepType {
  def stringified: String = this match {
    // ... existing cases ...
    case StepType.YourStep => "your_step"
  }
}

object StepType {
  // ... existing case objects ...
  case object YourStep extends StepType
  
  implicit val encodeStepStatus: Encoder[StepType] =
    Encoder.encodeString.contramap[StepType] {
      // ... existing cases ...
      case YourStep => "your_step"
    }

  implicit val decodeStepStatus: Decoder[StepType] = Decoder.decodeString.emap {
    // ... existing cases ...
    case "your_step" | "your_alias" => Right(YourStep)
    // ...
  }
}
```

2. Add metadata to `StepTypeRegistry`:

```scala
private val registry: Map[StepType, StepTypeMetadata] = Map(
  // ... existing entries ...
  StepType.YourStep -> StepTypeMetadata(
    stepType = StepType.YourStep,
    identifier = "your_step",
    aliases = List("your_alias"),  // Optional alternative names
    hasResource = true,  // Set to false if doesn't use resources
    discriminatorFields = List("field1", "field2")  // Fields unique to your step for JSON decoding
  )
)
```

**Key Points:**
- `identifier`: Primary string used in JSON for this step type
- `aliases`: Alternative names that map to the same step (optional)
- `hasResource`: Whether this step uses external resources
- `discriminatorFields`: JSON fields used to identify this step type during decoding

---

## Additional Updates (Optional)

### Variable Substitution

If your step has fields that can contain variables (like `${stepName.field}`), you need to update these files:

1. **`VariableSubstitutionService.scala`** - Add pattern match case in `substituteVariablesInStepMeta`:

```scala
case yourMeta: YourStepMeta =>
  yourMeta.copy(
    field1 = VariableSubstitution.substituteVariables(your Meta.field1, stepResponses)
  )
```

2. **`RuntimeVariableSubstitution.scala`** - Add pattern match case in `substituteVariablesInStepMeta`:

```scala
case yourMeta: YourStepMeta =>
  yourMeta.copy(
    field1 = substituteInString(yourMeta.field1, variableMap)
  )
```

3. **`VariableSubstitution.scala`** - Add pattern match case in `extractStringFieldsFromStepMeta`:

```scala
case yourMeta: YourStepMeta =>
  List(yourMeta.field1, yourMeta.field2).flatten
```

4. **`FlowServiceAdapter.scala`** - Add pattern match case in `extractAllTextFromSteps`:

```scala
case yourMeta: YourStepMeta =>
  textBuilder.append(yourMeta.field1).append(" ")
```

> **Note:** These files still use pattern matching. Future enhancements may introduce a trait-based approach to eliminate these manual touchpoints.

---

## Testing Your Step

### 1. Unit Tests

Create a test spec in `workers/src/test/scala/ab/async/tester/workers/app/runner/`:

```scala
class YourStepRunnerSpec extends AnyWordSpec with Matchers with MockFactory {
  // Test setup, execution, error handling, etc.
}
```

### 2. Integration Test

1. Start the application with Docker Compose:
   ```bash
   docker-compose up -d
   sbt run
   ```

2. Create a flow with your new step via API:
   ```bash
   curl -X POST http://localhost:9000/api/flows \
     -H "Content-Type: application/json" \
     -d '{
       "name": "Test Your Step",
       "steps": [
         {
           "name": "test_step",
           "stepType": "your_step",
           "meta": {
             "resourceId": "res-123",
             "field1": "value1"
           }
         }
       ]
     }'
   ```

3. Execute the flow and verify results

---

## Complete Example: Echo Step

Here's a complete example of adding a simple "Echo" step that just returns the input:

### 1. EchoStepMeta.scala

```scala
package ab.async.tester.domain.step

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class EchoStepMeta(
    message: String
) extends StepMeta  // No resource needed

object EchoStepMeta {
  implicit val encoder: Encoder[EchoStepMeta] = deriveEncoder[EchoStepMeta]
  implicit val decoder: Decoder[EchoStepMeta] = deriveDecoder[EchoStepMeta]
}
```

### 2. EchoStepRunner.scala

```scala
package ab.async.tester.workers.app.runner

import ab.async.tester.domain.execution.ExecutionStep
import ab.async.tester.domain.step.{EchoStepMeta, EchoResponse, StepResponse}
import ab.async.tester.library.substitution.VariableSubstitutionService
import com.google.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EchoStepRunner @Inject()(
  protected val variableSubstitutionService: VariableSubstitutionService
)(implicit ec: ExecutionContext) extends BaseStepRunner {
  
  override protected val runnerName: String = "EchoStepRunner"

  override protected def executeStep(
      step: ExecutionStep,
      previousResults: List[StepResponse]
  ): Future[StepResponse] = {
    val echoMeta = step.meta.asInstanceOf[EchoStepMeta]
    
    Future.successful(
      createSuccessResponse(step, EchoResponse(echoMeta.message))
    )
  }
}
```

### 3. Register in StepType.scala and StepTypeRegistry.scala

```scala
// In StepType.scala
case object Echo extends StepType

// In StepTypeRegistry.scala
StepType.Echo -> StepTypeMetadata(
  stepType = StepType.Echo,
  identifier = "echo",
  aliases = List.empty,
  hasResource = false,
  discriminatorFields = List("message")
)
```

---

## Troubleshooting

### Step not found during execution
- Check that `StepRunnerRegistryImpl` has the runner injected in its constructor
- Verify the runner is bound in `WorkerModule`

### JSON decoding fails
- Ensure `discriminatorFields` in `StepTypeRegistry` are unique and present in your JSON
- Check encoder/decoder are defined in companion object

### Resource not found
- Verify `hasResource = true` in `StepTypeRegistry`
- Ensure the step meta extends `StepMetaWithResource`
- Check the resource exists in the database

---

## Summary

Adding a new step type is now streamlined to **3 main files**:

1. **Step Meta** - Define your step's configuration
2. **Step Runner** - Implement your step's logic
3. **Step Registry** - Register your step type

The centralized registry pattern eliminates 75% of the boilerplate that was previously required!
