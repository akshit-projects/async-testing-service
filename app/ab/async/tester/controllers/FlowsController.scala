package ab.async.tester.controllers

import ab.async.tester.actors.WebSocketFlowActor
import ab.async.tester.models.flow.Floww
import ab.async.tester.models.requests.flow.GetFlowsRequest
import ab.async.tester.service.flows.FlowServiceTrait
import ab.async.tester.utils.{DecodingUtils, MetricUtils}
import ab.async.tester.exceptions.ValidationException
import ab.async.tester.models.response.GenericError
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Flow => AkkaFlow}
import akka.stream.Materializer
import com.google.inject.{Inject, Singleton}
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.Logger
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents, WebSocket}
import play.api.http.websocket.{Message, TextMessage}
import play.api.libs.streams.ActorFlow

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class FlowsController @Inject()(
  cc: ControllerComponents, 
  flowService: FlowServiceTrait
)(implicit ec: ExecutionContext, mat: Materializer, system: ActorSystem) extends AbstractController(cc) {

  private implicit val logger: Logger = Logger(this.getClass)

  def getFlows: Action[AnyContent] = Action.async { implicit request =>
    MetricUtils.withAsyncAPIMetrics("getFlows") {
      // Extract query params directly or use empty request
      val search = request.getQueryString("search")
      val limit = request.getQueryString("limit").map(_.toInt).getOrElse(10)
      val page = request.getQueryString("page").map(_.toInt).getOrElse(0)
      val ids = request.getQueryString("ids").map(_.split(",").toList)
      
      val getFlowRequest = GetFlowsRequest(search, limit, page, ids)
      
      flowService.getFlows(getFlowRequest).map { flows =>
        Ok(flows.asJson.noSpaces)
      }
    }
  }
  
  // Get a single flow by ID
  def getFlow(id: String): Action[AnyContent] = Action.async { implicit request =>
    MetricUtils.withAsyncAPIMetrics("getFlow") {
      flowService.getFlow(id).map {
        case Some(flow) => Ok(flow.asJson.noSpaces)
        case None => NotFound(Map("message" -> s"Flow not found with ID: $id").asJson.noSpaces)
      } recover {
        case e: Exception =>
          logger.error(s"Error getting flow $id: ${e.getMessage}", e)
          InternalServerError(Map("message" -> "An unexpected error occurred").asJson.noSpaces)
      }
    }
  }

  // Route to add a new flow
  def addFlow(): Action[AnyContent] = Action.async { implicit request =>
    MetricUtils.withAsyncAPIMetrics("addFlow") {
      request.body.asJson match {
        case Some(json) =>
          val flow = DecodingUtils.decodeJsValue[Floww](json)
          flowService.addFlow(flow).map { response =>
            // Check if the flow was an existing one with the same name
            if (flow.id.isDefined && flow.id.get != response.id.getOrElse("")) {
              Ok(GenericError(s"Flow with name '${flow.name}' already exists") .asJson.noSpaces)
            } else {
              Created(response.asJson.noSpaces)
            }
          } recover {
            case e: ValidationException =>
              BadRequest(Map("message" -> e.getMessage).asJson.noSpaces)
            case e: Exception =>
              logger.error(s"Error adding flow: ${e.getMessage}", e)
              InternalServerError(Map("message" -> "An unexpected error occurred").asJson.noSpaces)
          }
        case None =>
          Future.successful(BadRequest(Map("message" -> "Missing request body").asJson.noSpaces))
      }
    }
  }
  
  // Route to update an existing flow
  def updateFlow(): Action[AnyContent] = Action.async { implicit request =>
    MetricUtils.withAsyncAPIMetrics("updateFlow") {
      request.body.asJson match {
        case Some(json) =>
          val flow = DecodingUtils.decodeJsValue[Floww](json)
          
          flowService.updateFlow(flow).map { success =>
            if (success) {
              Ok("")
            } else {
              NotFound(Map("message" -> "Flow not found or update failed").asJson.noSpaces)
            }
          } recover {
            case e: ValidationException =>
              BadRequest(Map("message" -> e.getMessage).asJson.noSpaces)
            case e: Exception =>
              logger.error(s"Error updating flow: ${e.getMessage}", e)
              InternalServerError(Map("message" -> "An unexpected error occurred").asJson.noSpaces)
          }
        case None =>
          Future.successful(BadRequest(Map("message" -> "Missing request body").asJson.noSpaces))
      }
    }
  }

  // Route to validate flow steps
  def validateFlow(): Action[AnyContent] = Action.async { implicit request =>
    MetricUtils.withAsyncAPIMetrics("validateFlow") {
      request.body.asJson match {
        case Some(json) =>
          val flow = DecodingUtils.decodeJsValue[Floww](json)
          
          Try(flowService.validateSteps(flow)) match {
            case Success(_) => 
              Future.successful(Ok(""))
            case Failure(e: ValidationException) =>
              Future.successful(BadRequest(Map("message" -> e.getMessage).asJson.noSpaces))
            case Failure(e) =>
              logger.error(s"Error validating flow: ${e.getMessage}", e)
              Future.successful(InternalServerError(Map("message" -> "An unexpected error occurred").asJson.noSpaces))
          }
        case None =>
          Future.successful(BadRequest(Map("message" -> "Missing request body").asJson.noSpaces))
      }
    }
  }

  // Route to run the flow
  def runFlow(): WebSocket = {
    WebSocket.accept[Message, Message] { request =>
      logger.info(s"WebSocket connection established for flow execution")

      val textFrameFlow = Flow[Message].collect {
        case TextMessage(text) => text
      }

      val actorFlow = ActorFlow.actorRef[String, String] { out =>
        WebSocketFlowActor.props(out, flowService)
      }

      val stringToMessageFlow = Flow[String].map(TextMessage.apply)

      textFrameFlow
        .via(actorFlow)
        .via(stringToMessageFlow)
    }
  }
}