package ab.async.tester.workers.app.runner
class ScriptRunner{}
//@Singleton
//class ScriptStepRunner @Inject()(
//                                )(implicit ec: ExecutionContext) extends BaseStepRunner {
//
//  override protected val runnerName: String = "ScriptStepRunner"
//
//  private val dockerClient: DockerClient =
//    DockerClientBuilder.getInstance().build()
//
//  override protected def executeStep(
//                                      step: ExecutionStep,
//                                      previousResults: List[StepResponse]
//                                    ): Future[StepResponse] = Future {
//
//    val meta = step.meta.asInstanceOf[ScriptStepMeta]
//
//    val workspace = Files.createTempDirectory("script-step")
//
//    try {
//      val inputPath = workspace.resolve("input.json")
//      val scriptPath = workspace.resolve("script.js")
//      val wrapperPath = workspace.resolve("wrapper.js")
//      val outputPath = workspace.resolve("output.json")
//
//      // 1. Serialize previous results
//      Files.writeString(inputPath, Json.prettyPrint(Json.toJson(previousResults)))
//
//      // 2. Write user script
//      Files.writeString(scriptPath, meta.script)
//
//      // 3. Wrapper
//      Files.writeString(wrapperPath, wrapperJs)
//
//      // 4. Create container
//      val container = dockerClient.createContainerCmd("node:18-alpine")
//        .withCmd("node", "/workspace/wrapper.js")
//        .withHostConfig(
//          HostConfig.newHostConfig()
//            .withBinds(
//              new Bind(
//                workspace.toAbsolutePath.toString,
//                new Volume("/workspace")
//              )
//            )
//        )
//        .exec()
//
//      val containerId = container.getId
//
//      try {
//        dockerClient.startContainerCmd(containerId).exec()
//
//        // Wait with basic timeout
//        dockerClient.waitContainerCmd(containerId)
//          .start()
//          .awaitStatusCode()
//
//        if (!Files.exists(outputPath)) {
//          throw new RuntimeException("Script did not produce output.json")
//        }
//
//        val raw = Files.readString(outputPath)
//        val output = Json.parse(raw).as[Map[String, String]]
//
//        StepResponse(
//          name = step.name,
//          id = step.id.getOrElse(""),
//          status = StepStatus.SUCCESS,
//          response = ScriptStepResponse(
//            output = output,
//            logs = "" // optional: you can capture container logs later
//          )
//        )
//
//      } finally {
//        dockerClient.removeContainerCmd(containerId).withForce(true).exec()
//      }
//
//    } catch {
//      case e: Exception =>
//        logger.error(s"Script step failed: ${e.getMessage}", e)
//        StepResponse(
//          name = step.name,
//          id = step.id.getOrElse(""),
//          status = StepStatus.ERROR,
//          response = StepError(
//            expectedValue = None,
//            actualValue = Some(e.getMessage),
//            error = "Script execution failed"
//          )
//        )
//    } finally {
//      deleteRecursive(workspac
