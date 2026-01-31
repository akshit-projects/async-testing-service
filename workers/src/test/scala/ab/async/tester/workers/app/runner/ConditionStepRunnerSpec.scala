package ab.async.tester.workers.app.runner

import ab.async.tester.domain.enums.{ConditionOperator, StepStatus, StepType}
import ab.async.tester.domain.execution.ExecutionStep
import ab.async.tester.domain.step._
import ab.async.tester.domain.flow.Floww
import ab.async.tester.library.substitution.VariableSubstitutionService
import ab.async.tester.library.repository.flow.FlowRepository
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ConditionStepRunnerSpec
    extends AnyFlatSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures {

  val mockVariableSubstitutionService = mock[VariableSubstitutionService]
  val mockFlowRepository = mock[FlowRepository]
  val runner =
    new ConditionStepRunner(mockVariableSubstitutionService, mockFlowRepository)

  def createStep(meta: ConditionStepMeta): ExecutionStep = {
    ExecutionStep(
      id = Some("step-1"),
      name = "Condition Step",
      stepType = StepType.Condition,
      meta = meta,
      timeoutMs = 1000,
      status = StepStatus.PENDING,
      startedAt = Instant.now()
    )
  }

  val dummyStep = FlowStep(
    name = "Step1",
    stepType = StepType.Delay,
    meta = DelayStepMeta(100),
    timeoutMs = 1000
  )
  val linkedFlow = Floww(
    id = Some("flow-123"),
    name = "Linked Flow",
    creator = "test",
    steps = List(dummyStep)
  )

  "ConditionStepRunner" should "fetch flow and return steps for first matching branch (Equals)" in {
    val condition = Condition("5", ConditionOperator.Equals, "5")
    val branch = ConditionalBranch(condition, "flow-123")
    val meta = ConditionStepMeta(branches = List(branch))
    val step = createStep(meta)

    when(
      mockVariableSubstitutionService.substituteVariablesInStep(any(), any())
    ).thenAnswer(_.getArgument(0))
    when(mockFlowRepository.findById("flow-123"))
      .thenReturn(Future.successful(Some(linkedFlow)))

    val resultFuture = runner.runStep(step, List.empty)

    whenReady(resultFuture) { response =>
      response.status shouldBe StepStatus.SUCCESS
      val conditionResponse = response.response.asInstanceOf[ConditionResponse]
      conditionResponse.success shouldBe true
      conditionResponse.matchedChoice shouldBe "0"
      conditionResponse.generatedSteps should contain(dummyStep)
    }
  }

  it should "fetch flow and return steps for else branch if no condition matches" in {
    val condition = Condition("5", ConditionOperator.Equals, "6")
    val branch = ConditionalBranch(condition, "flow-123")
    val elseFlowId = "flow-456"
    val elseFlow = linkedFlow.copy(id = Some(elseFlowId), name = "Else Flow")

    val meta =
      ConditionStepMeta(branches = List(branch), elseFlowId = Some(elseFlowId))
    val step = createStep(meta)

    when(
      mockVariableSubstitutionService.substituteVariablesInStep(any(), any())
    ).thenAnswer(_.getArgument(0))
    when(mockFlowRepository.findById(elseFlowId))
      .thenReturn(Future.successful(Some(elseFlow)))

    val resultFuture = runner.runStep(step, List.empty)

    whenReady(resultFuture) { response =>
      response.status shouldBe StepStatus.SUCCESS
      val conditionResponse = response.response.asInstanceOf[ConditionResponse]
      conditionResponse.matchedChoice shouldBe "else"
      conditionResponse.generatedSteps should contain(elseFlow.steps.head)
    }
  }

  it should "return error if linked flow not found" in {
    val condition = Condition("5", ConditionOperator.Equals, "5")
    val branch = ConditionalBranch(condition, "flow-missing")
    val meta = ConditionStepMeta(branches = List(branch))
    val step = createStep(meta)

    when(
      mockVariableSubstitutionService.substituteVariablesInStep(any(), any())
    ).thenAnswer(_.getArgument(0))
    when(mockFlowRepository.findById("flow-missing"))
      .thenReturn(Future.successful(None))

    val resultFuture = runner.runStep(step, List.empty)

    whenReady(resultFuture) { response =>
      response.status shouldBe StepStatus.ERROR
      response.response shouldBe a[StepError]
    }
  }

  it should "terminate with error if no match and terminateOnNoMatch is true" in {
    val condition = Condition("5", ConditionOperator.Equals, "6")
    val branch = ConditionalBranch(condition, "flow-123")
    val meta =
      ConditionStepMeta(branches = List(branch), terminateOnNoMatch = true)
    val step = createStep(meta)

    when(
      mockVariableSubstitutionService.substituteVariablesInStep(any(), any())
    ).thenAnswer(_.getArgument(0))

    val resultFuture = runner.runStep(step, List.empty)

    whenReady(resultFuture) { response =>
      response.status shouldBe StepStatus.ERROR
      response.response shouldBe a[StepError]
    }
  }

  it should "succeed with empty steps if no match and terminateOnNoMatch is false" in {
    val condition = Condition("5", ConditionOperator.Equals, "6")
    val branch = ConditionalBranch(condition, "flow-123")
    val meta =
      ConditionStepMeta(branches = List(branch), terminateOnNoMatch = false)
    val step = createStep(meta)

    when(
      mockVariableSubstitutionService.substituteVariablesInStep(any(), any())
    ).thenAnswer(_.getArgument(0))

    val resultFuture = runner.runStep(step, List.empty)

    whenReady(resultFuture) { response =>
      response.status shouldBe StepStatus.SUCCESS
      val conditionResponse = response.response.asInstanceOf[ConditionResponse]
      conditionResponse.matchedChoice shouldBe "none"
      conditionResponse.generatedSteps shouldBe empty
    }
  }
}
