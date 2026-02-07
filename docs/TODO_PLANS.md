Problem Statement
Currently, adding a new step type requires manual updates in 10 different locations across the codebase:

StepType enum - Add new case object and string mappings
StepMeta companion object - Add encoder/decoder for the new meta type
StepMeta encoder - Add pattern match case
StepMeta decoder - Add field detection logic
StepRunnerRegistryImpl - Add constructor parameter and registration call
FlowServiceImpl.extractResourceId - Add pattern match case (if step uses resources)
VariableSubstitution.extractStringFieldsFromStepMeta - Add pattern match case
VariableSubstitutionService.substituteVariablesInStepMeta - Add pattern match case + substitution method
RuntimeVariableSubstitution.substituteVariablesInStepMeta - Add pattern match case
FlowServiceAdapter.extractAllTextFromSteps - Add pattern match case
This creates significant friction for developers and increases the chance of errors when adding new step types.

User Review Required
IMPORTANT

This refactoring will change how step types are registered and configured. The changes are backward compatible for existing steps but introduce a new pattern for future step additions.

Breaking Changes
None - all existing steps will continue to work
Design Decisions
Centralized Registry: Introduce StepTypeRegistry to hold all step type metadata
Trait-based Metadata: Each step meta will implement a StepMetaWithResource trait to declare if it uses resources
Reflection-free: Use explicit registration rather than reflection for better type safety and performance
Proposed Changes
Domain Layer
[MODIFY] 

StepMeta.scala
Changes:

Add StepMetaWithResource trait for steps that use resources
Make all resource-using step metas extend this trait
Refactor encoder/decoder to use a registry pattern instead of hardcoded pattern matching
Benefits:

Resource extraction becomes automatic via trait
Adding new step metas no longer requires updating the companion object's pattern matching
[NEW] 

StepTypeRegistry.scala
Purpose: Centralized registry for step type metadata

Contents:

case class StepTypeMetadata(
  stepType: StepType,
  identifier: String,
  aliases: List[String] = List.empty,
  hasResource: Boolean,
  discriminatorFields: List[String] // Fields used to identify this step type in JSON
)
object StepTypeRegistry {
  private val registry: Map[StepType, StepTypeMetadata] = Map(
    StepType.HttpRequest -> StepTypeMetadata(
      stepType = StepType.HttpRequest,
      identifier = "http",
      hasResource = true,
      discriminatorFields = List("body", "method")
    ),
    // ... other step types
  )
  
  def getMetadata(stepType: StepType): StepTypeMetadata
  def fromIdentifier(identifier: String): Option[StepType]
  def getDiscriminatorFields: Map[List[String], StepType]
}
Benefits:

Single source of truth for step type configuration
Easy to add new step types - just add one entry to the registry
Discriminator fields are documented alongside the step type
[MODIFY] 

StepType.scala
Changes:

Use StepTypeRegistry for stringified method
Use StepTypeRegistry for encoder/decoder instead of hardcoded mappings
Benefits:

No need to update encoder/decoder when adding new step types
Domain - Variable Substitution
[MODIFY] 

VariableSubstitution.scala
Changes:

Add VariableFieldExtractor trait that each step meta can implement
Make step metas implement this trait to declare their variable-containing fields
Refactor extractStringFieldsFromStepMeta to use the trait instead of pattern matching
Alternative approach (simpler):

Keep pattern matching but add a helper method that throws a clear error if a step type is not handled
Add a comment pointing developers to this location when adding new steps
Benefits:

Either automatic extraction via trait OR clear error message pointing to the exact location to update
Library - Variable Substitution
[MODIFY] 

VariableSubstitutionService.scala
Changes:

Add VariableSubstitutable trait that step metas can implement
Each step meta implements a substituteVariables method
Refactor substituteVariablesInStepMeta to use the trait
Alternative approach (simpler):

Create a registry of substitution functions keyed by step type
Register substitution logic alongside step type metadata in StepTypeRegistry
Benefits:

No need to add pattern matching when adding new steps
Substitution logic is co-located with step definition
[MODIFY] 

RuntimeVariableSubstitution.scala
Changes:

Use the same VariableSubstitutable trait approach OR
Use registry pattern similar to VariableSubstitutionService
Benefits:

Consistent with VariableSubstitutionService approach
Automatic handling of new step types
API Layer
[MODIFY] 

FlowServiceAdapter.scala
Changes:

Add TextExtractable trait that step metas can implement
Refactor extractAllTextFromSteps to use the trait
Alternative approach:

Add a getTextFields method to the VariableSubstitutable trait
Reuse the same trait for both substitution and text extraction
Benefits:

No need to update when adding new steps
Text extraction logic is co-located with step definition
[MODIFY] 

FlowServiceImpl.scala
Changes:

Refactor extractResourceId to use the StepMetaWithResource trait
Remove pattern matching - just check if meta implements the trait
private def extractResourceId(step: FlowStep): Option[String] = {
  step.meta match {
    case meta: StepMetaWithResource => Some(meta.resourceId)
    case _ => None
  }
}
Benefits:

No need to update this method when adding new step types with resources
Workers Layer
[MODIFY] 

StepRunner.scala
Changes:

Add StepRunnerMetadata case class to hold runner information
Modify StepRunnerRegistry trait to support metadata-based registration
Update StepRunnerRegistryImpl to use automatic registration via a list of runners
trait StepRunnerWithMetadata extends StepRunner {
  def metadata: StepRunnerMetadata
}
case class StepRunnerMetadata(
  stepType: StepType,
  runnerName: String
)
class StepRunnerRegistryImpl @Inject()(
  allRunners: Set[StepRunnerWithMetadata] // Guice will inject all bound runners
) extends StepRunnerRegistry {
  // Auto-register all runners
  allRunners.foreach { runner =>
    registerRunner(runner.metadata.stepType.toString, runner)
  }
}
Benefits:

No need to manually add constructor parameters and registration calls
Guice automatically discovers all step runners
[MODIFY] 

WorkerModule.scala
Changes:

Use Guice Multibinder to automatically collect all step runners
Remove individual step runner bindings from configure()
override def configure(): Unit = {
  val runnerBinder = Multibinder.newSetBinder(binder(), classOf[StepRunnerWithMetadata])
  // Runners will self-register via @Provides methods or explicit bindings
}
Benefits:

Adding a new step runner just requires binding it once
No need to update the registry manually
Documentation
[MODIFY] 

LLD.md
Changes:

Update "Adding a New Step Type" section with the new simplified process
Add examples showing the new 2-file approach
[NEW] 

STEP_CREATION_GUIDE.md
Purpose: Step-by-step guide for creating new step types

Contents:

Template for step meta class
Template for step runner class
Checklist of what to update
Examples from existing steps
Verification Plan
Automated Tests
Existing Step Tests

cd /Users/akshitpersonal/Downloads/async-testing-service-main
sbt "workers/test"
Verify all existing step runner tests still pass

Flow Service Tests

sbt "test:testOnly *FlowServiceSpec"
Verify flow validation and resource extraction still works

Integration Test - Create New Test Step

Create a dummy "EchoStep" following the new pattern
Verify it registers correctly
Verify it executes successfully
Remove the dummy step after verification
Manual Verification
Start the application

docker-compose up -d
sbt run
Create a flow with HTTP step via API

curl -X POST http://localhost:9000/api/flows \
  -H "Content-Type: application/json" \
  -d @test-flow.json
Execute the flow and verify it completes successfully

Check logs to ensure no errors related to step registration

Implementation Order
Create StepTypeRegistry and StepTypeMetadata
Add StepMetaWithResource trait and update step metas
Refactor StepType encoder/decoder to use registry
Refactor FlowServiceImpl.extractResourceId
Add StepRunnerWithMetadata trait
Update all step runners to implement the trait
Refactor StepRunnerRegistryImpl for auto-registration
Update WorkerModule to use Multibinder
Update VariableSubstitution (choose trait or helper approach)
Update documentation
Run all tests
Manual verification
Post-Implementation: Adding a New Step
Before (10 touchpoints):
Create step meta class
Update StepType enum (case object + stringified + encoder + decoder)
Update StepMeta encoder (pattern match)
Update StepMeta decoder (field detection)
Create step runner
Update StepRunnerRegistryImpl (constructor + registration)
Update FlowServiceImpl.extractResourceId (if uses resources)
Update VariableSubstitution.extractStringFieldsFromStepMeta
Update VariableSubstitutionService.substituteVariablesInStepMeta + add substitution method
Update RuntimeVariableSubstitution.substituteVariablesInStepMeta
Update FlowServiceAdapter.extractAllTextFromSteps
Update WorkerModule (bind step runner)
After (2-3 touchpoints):
Create step meta class implementing StepMetaWithResource and VariableSubstitutable traits
Create step runner implementing StepRunnerWithMetadata trait
Add one entry to StepTypeRegistry configuration
Reduction: 12 touchpoints → 3 touchpoints (75% reduction)