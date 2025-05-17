package ab.async.tester.actors

import ab.async.tester.models.flow.Floww
import ab.async.tester.service.flows.FlowServiceTrait
import ab.async.tester.utils.DecodingUtils
import akka.actor.{Actor, ActorRef, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.circe.syntax._
import play.api.Logger

import scala.concurrent.ExecutionContext

object WebSocketFlowActor {
  def props(out: ActorRef, flowService: FlowServiceTrait)(implicit ec: ExecutionContext, mat: Materializer): Props =
    Props(new WebSocketFlowActor(out, flowService))
    
  // Messages that can be sent to this actor
  case class RunFlow(message: String)
  case class FlowComplete(status: String)
  case class FlowError(error: String)
}

class WebSocketFlowActor(out: ActorRef, flowService: FlowServiceTrait)
                       (implicit ec: ExecutionContext, mat: Materializer) extends Actor {
  import WebSocketFlowActor._
  
  private implicit val logger: Logger = Logger(this.getClass)
  
  override def receive: Receive = {
    case message: String =>
      handleFlowMessage(message)
      
    case FlowComplete(status) =>
      out ! status
      
    case FlowError(error) =>
      out ! error
  }
  
  private def handleFlowMessage(message: String): Unit = {
    try {
      val flow = DecodingUtils.decodeWithErrorLogs[Floww](message)
      logger.info(s"Received flow execution request for flow: ${flow.name}")
      
      // Create a source from the flow service
      val statusSource = flowService.runFlow(flow)
        .map(status => status.asJson.noSpaces)
      
      // Consume the source and send each message to the client
      statusSource
        .runForeach { statusMessage =>
          out ! statusMessage
        }
        .recover {
          case e: Exception =>
            logger.error(s"Error in flow execution: ${e.getMessage}", e)
            val errorMessage = s"""{"type":"error","message":"${e.getMessage}","errorCode":"EXECUTION_ERROR"}"""
            out ! errorMessage
        }
    } catch {
      case e: Exception =>
        logger.error(s"Error parsing flow message: ${e.getMessage}", e)
        val errorMessage = s"""{"type":"error","message":"Invalid flow format: ${e.getMessage}","errorCode":"INVALID_FLOW"}"""
        out ! errorMessage
    }
  }
} 