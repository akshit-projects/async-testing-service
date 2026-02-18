package ab.async.tester.workers.app.runner

import ab.async.tester.domain.enums.StepStatus
import ab.async.tester.domain.execution.ExecutionStep
import ab.async.tester.domain.step.metas.ScriptStepMeta
import ab.async.tester.domain.step.{ScriptStepResponse, StepError, StepResponse}
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DockerClientBuilder
import com.google.inject.{Inject, Singleton}
import play.api.libs.json.Json

import java.nio.file.{Files, Path}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Using

@Singleton
class ScriptStepRunner @Inject()(
                                )(implicit ec: ExecutionContext) extends BaseStepRunner {

  override protected val runnerName: String = "ScriptStepRunner"

  private val dockerClient: DockerClient =
    DockerClientBuilder.getInstance().build()

  override protected def executeStep(
                                      step: ExecutionStep,
                                      previousResults: List[StepResponse]
                                    ): Future[StepResponse] = Future {

    val meta = step.meta.asInstanceOf[ScriptStepMeta]

    val workspace = Files.createTempDirectory("script-step")

    try {
      val inputPath = workspace.resolve("input.json")
      val scriptPath = workspace.resolve("script.js")
      val wrapperPath = workspace.resolve("wrapper.js")
      val outputPath = workspace.resolve("output.json")

      // 1. Serialize previous results
      Files.writeString(inputPath, Json.prettyPrint(Json.toJson(previousResults)))

      // 2. Write user script
      Files.writeString(scriptPath, meta.script)

      // 3. Wrapper
      Files.writeString(wrapperPath, wrapperJs)

      // 4. Create container
      val container = dockerClient.createContainerCmd("node:18-alpine")
        .withCmd("node", "/workspace/wrapper.js")
        .withHostConfig(
          com.github.dockerjava.api.model.HostConfig.newHostConfig()
            .withBinds(
              new com.github.dockerjava.api.model.Bind(
                workspace.toAbsolutePath.toString,
                new com.github.dockerjava.api.model.Volume("/workspace")
              )
            )
        )
        .exec()

      val containerId = container.getId

      try {
        dockerClient.startContainerCmd(containerId).exec()

        // Wait with basic timeout
        dockerClient.waitContainerCmd(containerId)
          .start()
          .awaitStatusCode()

        if (!Files.exists(outputPath)) {
          throw new RuntimeException("Script did not produce output.json")
        }

        val raw = Files.readString(outputPath)
        val output = Json.parse(raw).as[Map[String, String]]

        StepResponse(
          name = step.name,
          id = step.id.getOrElse(""),
          status = StepStatus.SUCCESS,
          response = ScriptStepResponse(
            output = output,
            logs = "" // optional: you can capture container logs later
          )
        )

      } finally {
        dockerClient.removeContainerCmd(containerId).withForce(true).exec()
      }

    } catch {
      case e: Exception =>
        logger.error(s"Script step failed: ${e.getMessage}", e)
        StepResponse(
          name = step.name,
          id = step.id.getOrElse(""),
          status = StepStatus.ERROR,
          response = StepError(
            expectedValue = None,
            actualValue = Some(e.getMessage),
            error = "Script execution failed"
          )
        )
    } finally {
      deleteRecursive(workspac
