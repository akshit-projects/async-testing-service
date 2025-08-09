package ab.async.tester.controllers

import ab.async.tester.domain.requests.RunFlowRequest
import ab.async.tester.service.flows.FlowExecutionService
import akka.NotUsed
import akka.actor.ActorSystem
import io.circe.generic.auto._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import io.circe.jawn.decode
import play.api.mvc._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RunFlowController @Inject()(
                                   cc: ControllerComponents,
                                   flowExecService: FlowExecutionService,
                                )(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def runFlow(): WebSocket = WebSocket.acceptOrResult[String, String] { _ =>
    Future.successful(Right(handleWebSocket()))
  }

  private def handleWebSocket(): Flow[String, String, NotUsed] = {
    Flow[String].prefixAndTail(1).mapAsync(1) {
      case (Seq(firstMsg), _) =>
        decode[RunFlowRequest](firstMsg) match {
          case Left(err) =>
            Future.failed(new RuntimeException(s"Invalid RunRequest JSON: $err"))
          case Right(runRequest) =>
            flowExecService.startExecution(runRequest)
        }
    }.flatMapConcat { execStreams =>
      execStreams.source
        .watchTermination() { (_, done) =>
          done.foreach { _ =>
            flowExecService.stopExecution(execStreams.executionId)
          }
        }
    }
  }
}
