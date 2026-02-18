package ab.async.tester.domain.step

import ab.async.tester.domain.enums.{StepStatus, StepType}
import ab.async.tester.domain.step.metas.HttpStepMeta
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class VariableSubstitutionSpec extends AnyFlatSpec with Matchers {

  "VariableSubstitution" should "extract variable references correctly" in {
    val input = "Authorization: ${step1.responseHeaders.Authorization}"
    val refs = VariableSubstitution.extractVariableReferences(input)
    
    refs should have size 1
    refs.head.stepName shouldBe "step1"
    refs.head.fieldPath shouldBe List("responseHeaders", "Authorization")
    refs.head.originalExpression shouldBe "step1.responseHeaders.Authorization"
  }

  it should "extract multiple variable references" in {
    val input = "User ${user.response.id} has status ${status.response.value}"
    val refs = VariableSubstitution.extractVariableReferences(input)
    
    refs should have size 2
    refs(0).stepName shouldBe "user"
    refs(0).fieldPath shouldBe List("response", "id")
    refs(1).stepName shouldBe "status"
    refs(1).fieldPath shouldBe List("response", "value")
  }

  it should "substitute variables in HTTP response" in {
    val httpResponse = HttpResponse(
      status = 200,
      response = """{"userId": "123", "name": "John"}""",
      headers = Map("Authorization" -> "Bearer token123")
    )
    
    val stepResponse = StepResponse(
      name = "login",
      status = StepStatus.SUCCESS,
      response = httpResponse,
      id = "step1"
    )
    
    val stepResponses = Map("login" -> stepResponse)
    
    // Test header extraction
    val headerResult = VariableSubstitution.substituteVariables(
      "Authorization: ${login.headers.Authorization}",
      stepResponses
    )
    headerResult shouldBe "Authorization: Bearer token123"
    
    // Test JSON response extraction
    val jsonResult = VariableSubstitution.substituteVariables(
      "User ID: ${login.response.userId}",
      stepResponses
    )
    jsonResult shouldBe "User ID: 123"
    
    // Test status extraction
    val statusResult = VariableSubstitution.substituteVariables(
      "Status: ${login.status}",
      stepResponses
    )
    statusResult shouldBe "Status: 200"
  }

  it should "substitute variables in Kafka response" in {
    val kafkaResponse = KafkaMessagesResponse(
      messages = List(
        KafkaMessage(Some("key1"), "value1"),
        KafkaMessage(Some("key2"), "value2")
      )
    )
    
    val stepResponse = StepResponse(
      name = "kafkaStep",
      status = StepStatus.SUCCESS,
      response = kafkaResponse,
      id = "step2"
    )
    
    val stepResponses = Map("kafkaStep" -> stepResponse)
    
    // Test message count
    val countResult = VariableSubstitution.substituteVariables(
      "Received ${kafkaStep.messages.count} messages",
      stepResponses
    )
    countResult shouldBe "Received 2 messages"
    
    // Test specific message value
    val valueResult = VariableSubstitution.substituteVariables(
      "First message: ${kafkaStep.messages.0.value}",
      stepResponses
    )
    valueResult shouldBe "First message: value1"
    
    // Test specific message key
    val keyResult = VariableSubstitution.substituteVariables(
      "Second key: ${kafkaStep.messages.1.key}",
      stepResponses
    )
    keyResult shouldBe "Second key: key2"
  }

  it should "validate variable references in flow steps" in {
    val step1 = FlowStep(
      name = "step1",
      stepType = StepType.HttpRequest,
      meta = HttpStepMeta(resourceId = "api1"),
      timeoutMs = 5000
    )
    
    val step2 = FlowStep(
      name = "step2",
      stepType = StepType.HttpRequest,
      meta = HttpStepMeta(
        resourceId = "api2",
        headers = Some(Map("Authorization" -> "${step1.headers.Authorization}"))
      ),
      timeoutMs = 5000
    )
    
    val step3 = FlowStep(
      name = "step3",
      stepType = StepType.HttpRequest,
      meta = HttpStepMeta(
        resourceId = "api3",
        body = Some("""{"userId": "${nonexistent.response.id}"}""")
      ),
      timeoutMs = 5000
    )
    
    val validFlow = List(step1, step2)
    val invalidFlow = List(step1, step3)
    
    // Valid flow should pass validation
    val validErrors = VariableSubstitution.validateVariableReferences(validFlow)
    validErrors shouldBe empty
    
    // Invalid flow should fail validation
    val invalidErrors = VariableSubstitution.validateVariableReferences(invalidFlow)
    invalidErrors should not be empty
    invalidErrors.head should include("references undefined step 'nonexistent'")
  }

  it should "handle complex JSON path extraction" in {
    val httpResponse = HttpResponse(
      status = 200,
      response = """{"user": {"profile": {"id": "123", "settings": {"theme": "dark"}}}, "items": [{"name": "item1"}, {"name": "item2"}]}""",
      headers = Map.empty
    )
    
    val stepResponse = StepResponse(
      name = "complexStep",
      status = StepStatus.SUCCESS,
      response = httpResponse,
      id = "step1"
    )
    
    val stepResponses = Map("complexStep" -> stepResponse)
    
    // Test nested object extraction
    val nestedResult = VariableSubstitution.substituteVariables(
      "User ID: ${complexStep.response.user.profile.id}",
      stepResponses
    )
    nestedResult shouldBe "User ID: 123"
    
    // Test deeply nested extraction
    val deepResult = VariableSubstitution.substituteVariables(
      "Theme: ${complexStep.response.user.profile.settings.theme}",
      stepResponses
    )
    deepResult shouldBe "Theme: dark"
    
    // Test array extraction
    val arrayResult = VariableSubstitution.substituteVariables(
      "First item: ${complexStep.response.items.0.name}",
      stepResponses
    )
    arrayResult shouldBe "First item: item1"
  }

  it should "throw exception for missing step reference" in {
    val stepResponses = Map.empty[String, StepResponse]
    
    assertThrows[RuntimeException] {
      VariableSubstitution.substituteVariables(
        "Value: ${missingStep.response.value}",
        stepResponses
      )
    }
  }

  it should "throw exception for missing field reference" in {
    val httpResponse = HttpResponse(
      status = 200,
      response = """{"id": "123"}""",
      headers = Map.empty
    )
    
    val stepResponse = StepResponse(
      name = "testStep",
      status = StepStatus.SUCCESS,
      response = httpResponse,
      id = "step1"
    )
    
    val stepResponses = Map("testStep" -> stepResponse)
    
    assertThrows[RuntimeException] {
      VariableSubstitution.substituteVariables(
        "Value: ${testStep.response.nonexistentField}",
        stepResponses
      )
    }
  }
}
