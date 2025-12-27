package ab.async.tester.controllers

import controllers.BaseControllerSpec
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}

class MetricsControllerSpec extends BaseControllerSpec {

  val cc = Helpers.stubControllerComponents()
  val controller = new MetricsController(cc)

  "MetricsController" should {
    "return metrics" in {
      val request = FakeRequest(GET, "/metrics")
      val result = controller.metrics().apply(request)

      status(result) mustBe OK
      contentType(result) mustBe Some("text/plain")
    }
  }
}
