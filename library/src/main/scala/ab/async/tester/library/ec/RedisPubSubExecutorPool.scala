package ab.async.tester.library.ec

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.ExecutionContext

object RedisPubSubExecutorPool {

  private val executorService: ExecutorService = Executors.newFixedThreadPool(5)

  // Wrap the ExecutorService in a Scala ExecutionContext
  private implicit val customExecutionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(executorService)

  def shutdown(): Unit = executorService.shutdown()

  def getExecutionContext: ExecutionContext = customExecutionContext
}
