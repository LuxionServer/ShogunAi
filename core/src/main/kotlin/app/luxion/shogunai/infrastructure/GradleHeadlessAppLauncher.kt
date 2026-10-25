package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.HeadlessAppLauncher
import app.luxion.shogunai.domain.model.HeadlessLaunch
import app.luxion.shogunai.domain.model.HeadlessLaunchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

class GradleHeadlessAppLauncher(
    private val executor: ShellCommandExecutor,
    private val scriptDirectory: String,
) : HeadlessAppLauncher {

    override suspend fun launch(launch: HeadlessLaunch): HeadlessLaunchResult {
        val script = writeInitScript()
        val log = Path.of(launch.logPath)
        withContext(Dispatchers.IO) {
            Files.createDirectories(log.parent)
            Files.writeString(log, "")
            launch.homePath?.let { Files.createDirectories(Path.of(it)) }
        }
        val result = executor.executeStreaming(
            command = command(launch, script),
            workingDirectory = launch.worktreePath,
            environment = emptyMap(),
        ) { line -> Files.writeString(log, line.text + "\n", StandardOpenOption.APPEND) }
        return HeadlessLaunchResult(result.exitCode, startedPid(result.stdout + "\n" + result.stderr))
    }

    override fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    override suspend fun stop(pid: Long) = withContext(Dispatchers.IO) {
        val handle = ProcessHandle.of(pid).orElse(null) ?: return@withContext
        val processes = handle.descendants().toList() + handle
        processes.forEach { it.destroy() }
        processes.forEach { process ->
            runCatching { process.onExit().get(STOP_GRACE_SECONDS, TimeUnit.SECONDS) }
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private suspend fun writeInitScript(): Path = withContext(Dispatchers.IO) {
        val script = Path.of(scriptDirectory, INIT_SCRIPT_NAME)
        Files.createDirectories(script.parent)
        if (!Files.exists(script) || Files.readString(script) != INIT_SCRIPT) Files.writeString(script, INIT_SCRIPT)
        script
    }

    companion object {
        const val INIT_SCRIPT_NAME = "shogunai-headless.init.gradle.kts"
        private const val STOP_GRACE_SECONDS = 5L
        private val STARTED_LINE = Regex("Started '[^']*' in background \\((\\d+)\\)")

        fun startedPid(output: String): Long? = STARTED_LINE.findAll(output).lastOrNull()?.groupValues?.get(1)?.toLongOrNull()

        fun command(launch: HeadlessLaunch, initScript: Path): List<String> = buildList {
            add(Path.of(launch.worktreePath, "gradlew").toString())
            add("--console=plain")
            add("-I")
            add(initScript.toString())
            add("-P$MODULE_PROPERTY=${launch.module}")
            launch.homePath?.let { add("-P$HOME_PROPERTY=$it") }
            add("-Pcompose.reload.devToolsHeadless=true")
            add("${launch.module}:hotRunAsync")
        }

        private const val MODULE_PROPERTY = "shogunai.headless.module"
        private const val HOME_PROPERTY = "shogunai.headless.home"

        val INIT_SCRIPT = """
            val headlessModule: String? = gradle.startParameter.projectProperties["$MODULE_PROPERTY"]
            val headlessHome: String? = gradle.startParameter.projectProperties["$HOME_PROPERTY"]
            if (headlessModule != null) {
                allprojects {
                    if (path == headlessModule) {
                        tasks.withType<JavaExec>().matching { it.name == "hotRun" }.configureEach {
                            systemProperty("apple.awt.UIElement", "true")
                            systemProperty("shogunai.headless", "true")
                            if (headlessHome != null) systemProperty("user.home", headlessHome)
                        }
                    }
                }
            }

        """.trimIndent()
    }
}
