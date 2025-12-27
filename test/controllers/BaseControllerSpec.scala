package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._
import org.mockito.MockitoSugar
import scala.concurrent.ExecutionContext

abstract class BaseControllerSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with MockitoSugar {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
}
