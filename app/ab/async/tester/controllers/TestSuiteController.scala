package ab.async.tester.controllers

import ab.async.tester.controllers.auth.AuthorizedAction
import ab.async.tester.domain.enums.ExecutionStatus
import ab.async.tester.domain.requests.RunTestSuiteRequest
import ab.async.tester.domain.testsuite.TestSuite
import ab.async.tester.domain.user.Permissions
import ab.async.tester.library.utils.{JsonParsers, MetricUtils}
import ab.async.tester.library.utils.JsonParsers.ResultHelpers
import ab.async.tester.service.testsuite.TestSuiteService
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.Logger
import play.api.mvc._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class TestSuiteController @Inject() (
    cc: ControllerComponents,
    testSuiteService: TestSuiteService,
    authorizedAction: AuthorizedAction
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  private val logger = Logger(this.getClass)

  /** GET /api/v1/test-suites?search=&creator=&enabled=&limit=&page= */
  def getTestSuites: Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.TESTSUITES_READ).async {
      implicit request =>
        MetricUtils.withAPIMetrics("getTestSuites") {
          val search = request.getQueryString("search")
          val creator = request.getQueryString("creator")
          val orgId = request.getQueryString("orgId")
          val teamId = request.getQueryString("teamId")
          val enabled = request
            .getQueryString("enabled")
            .flatMap(s => Try(s.toBoolean).toOption)
          val limit = request
            .getQueryString("limit")
            .flatMap(s => Try(s.toInt).toOption)
            .getOrElse(10)
          val page = request
            .getQueryString("page")
            .flatMap(s => Try(s.toInt).toOption)
            .getOrElse(0)

          testSuiteService
            .getTestSuites(search, creator, enabled, orgId, teamId, limit, page)
            .map { testSuites =>
              Ok(testSuites.asJson.noSpaces).as("application/json")
            }
            .recover { case ex =>
              logger.error("getTestSuites failed", ex)
              InternalServerError(
                Map("error" -> "failed to fetch test suites").asJsonNoSpaces
              )
            }
        }
    }

  /** GET /api/v1/test-suites/:id */
  def getTestSuite(id: String): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.TESTSUITES_READ).async {
      implicit request =>
        MetricUtils.withAPIMetrics("getTestSuite") {
          testSuiteService
            .getTestSuite(id)
            .map {
              case Some(testSuite) =>
                Ok(testSuite.asJson.noSpaces).as("application/json")
              case None =>
                NotFound(Map("error" -> "test suite not found").asJsonNoSpaces)
            }
            .recover { case ex =>
              logger.error(s"getTestSuite failed for id: $id", ex)
              InternalServerError(
                Map("error" -> "failed to fetch test suite").asJsonNoSpaces
              )
            }
        }
    }

  /** POST /api/v1/test-suites */
  def createTestSuite(): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.TESTSUITES_CREATE).async {
      implicit request =>
        MetricUtils.withAPIMetrics("createTestSuite") {
          JsonParsers.parseJsonBody[TestSuite](request)(implicitly, ec) match {
            case Left(result)     => Future.successful(result)
            case Right(testSuite) =>
              testSuiteService
                .createTestSuite(testSuite)
                .map { created =>
                  Created(created.asJson.noSpaces).as("application/json")
                }
                .recover {
                  case ex: IllegalArgumentException =>
                    logger.warn(
                      s"createTestSuite validation failed: ${ex.getMessage}"
                    )
                    BadRequest(Map("error" -> ex.getMessage).asJsonNoSpaces)
                  case ex =>
                    logger.error("createTestSuite failed", ex)
                    InternalServerError(
                      Map(
                        "error" -> "failed to create test suite"
                      ).asJsonNoSpaces
                    )
                }
          }
        }
    }

  /** PUT /api/v1/test-suites */
  def updateTestSuite(): Action[AnyContent] =
    authorizedAction.requirePermission(Permissions.TESTSUITES_UPDATE).async {
      implicit request =>
        MetricUtils.withAPIMetrics("updateTestSuite") {
          JsonParsers.parseJsonBody[TestSuite](request)(implicitly, ec) match {
            case Left(result)     => Future.successful(result)
            case Right(testSuite) =>
              if (testSuite.id.isEmpty) {
                Future.successful(
                  BadRequest(
                    Map(
                      "error" -> "test suite ID is required for update"
                    ).asJsonNoSpaces
                  )
                )
              } else {
                testSuiteService
                  .updateTestSuite(testSuite)
                  .map { updated =>
                    if (updated) {
                      Ok(Map("status" -> "updated").asJsonNoSpaces)
                    } else {
                      NotFound(
                        Map("error" -> "test suite not found").asJsonNoSpaces
                      )
                    }
                  }
                  .recover {
                    case ex: IllegalArgumentException =>
                      logger.warn(
                        s"updateTestSuite validation failed: ${ex.getMessage}"
                      )
                      BadRequest(Map("error" -> ex.getMessage).asJsonNoSpaces)
                    case ex =>
                      logger.error("updateTestSuite failed", ex)
                      InternalServerError(
                        Map(
                          "error" -> "failed to update test suite"
                        ).asJsonNoSpaces
                      )
                  }
              }
          }
        }
    }

  /** DELETE /api/v1/test-suites/:id */
  def deleteTestSuite(id: String): Action[AnyContent] = Action.async {
    implicit request =>
      MetricUtils.withAPIMetrics("deleteTestSuite") {
        testSuiteService
          .deleteTestSuite(id)
          .map { deleted =>
            if (deleted) {
              Ok(Map("status" -> "deleted").asJsonNoSpaces)
            } else {
              NotFound(Map("error" -> "test suite not found").asJsonNoSpaces)
            }
          }
          .recover { case ex =>
            logger.error(s"deleteTestSuite failed for id: $id", ex)
            InternalServerError(
              Map("error" -> "failed to delete test suite").asJsonNoSpaces
            )
          }
      }
  }

  /** POST /api/v1/test-suites/run */
  def runTestSuite(): Action[AnyContent] = Action.async { implicit request =>
    MetricUtils.withAPIMetrics("runTestSuite") {
      JsonParsers
        .parseJsonBody[RunTestSuiteRequest](request)(implicitly, ec) match {
        case Left(result)      => Future.successful(result)
        case Right(runRequest) =>
          testSuiteService
            .triggerTestSuite(runRequest)
            .map { execution =>
              Created(execution.asJson.noSpaces).as("application/json")
            }
            .recover {
              case ex: IllegalArgumentException =>
                logger.warn(s"runTestSuite validation failed: ${ex.getMessage}")
                BadRequest(Map("error" -> ex.getMessage).asJsonNoSpaces)
              case ex =>
                logger.error("runTestSuite failed", ex)
                InternalServerError(
                  Map("error" -> "failed to trigger test suite").asJsonNoSpaces
                )
            }
      }
    }
  }

  /** POST /api/v1/test-suites/validate */
  def validateTestSuite(): Action[AnyContent] = Action.async {
    implicit request =>
      MetricUtils.withAPIMetrics("validateTestSuite") {
        JsonParsers.parseJsonBody[TestSuite](request)(implicitly, ec) match {
          case Left(result)     => Future.successful(result)
          case Right(testSuite) =>
            testSuiteService
              .validateTestSuite(testSuite)
              .map { _ =>
                Ok(Map("status" -> "valid").asJsonNoSpaces)
              }
              .recover {
                case ex: IllegalArgumentException =>
                  logger.warn(s"validateTestSuite failed: ${ex.getMessage}")
                  BadRequest(Map("error" -> ex.getMessage).asJsonNoSpaces)
                case ex =>
                  logger.error("validateTestSuite failed", ex)
                  InternalServerError(
                    Map("error" -> "validation failed").asJsonNoSpaces
                  )
              }
        }
      }
  }

  /** GET /api/v1/test-suite-executions?testSuiteId=&limit=&page=&status= */
  def getTestSuiteExecutions(): Action[AnyContent] = Action.async {
    implicit request =>
      MetricUtils.withAPIMetrics("getTestSuiteExecutions") {
        val testSuiteId = request.getQueryString("testSuiteId")
        val limit = request
          .getQueryString("limit")
          .flatMap(s => Try(s.toInt).toOption)
          .getOrElse(10)
        val page = request
          .getQueryString("page")
          .flatMap(s => Try(s.toInt).toOption)
          .getOrElse(0)
        val statuses = request
          .getQueryString("status")
          .map(_.split(",").toList.map(_.asInstanceOf[ExecutionStatus]))

        testSuiteService
          .getTestSuiteExecutions(testSuiteId, limit, page, statuses)
          .map { executions =>
            Ok(executions.asJson.noSpaces).as("application/json")
          }
          .recover { case ex =>
            logger.error("getTestSuiteExecutions failed", ex)
            InternalServerError(
              Map(
                "error" -> "failed to fetch test suite executions"
              ).asJsonNoSpaces
            )
          }
      }
  }

  /** GET /api/v1/test-suite-executions/:id */
  def getTestSuiteExecution(id: String): Action[AnyContent] = Action.async {
    implicit request =>
      MetricUtils.withAPIMetrics("getTestSuiteExecution") {
        testSuiteService
          .getTestSuiteExecution(id)
          .map {
            case Some(execution) =>
              Ok(execution.asJson.noSpaces).as("application/json")
            case None =>
              NotFound(
                Map("error" -> "test suite execution not found").asJsonNoSpaces
              )
          }
          .recover { case ex =>
            logger.error(s"getTestSuiteExecution failed for id: $id", ex)
            InternalServerError(
              Map(
                "error" -> "failed to fetch test suite execution"
              ).asJsonNoSpaces
            )
          }
      }
  }
}
