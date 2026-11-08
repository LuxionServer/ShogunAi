package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.ProviderToolId
import app.luxion.shogunai.domain.model.ProviderToolProblem
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.joinPath
import kotlinx.coroutines.CancellationException

class CheckProviderToolsUseCase(
    private val executor: ShellCommandExecutor,
    private val findExecutable: (String) -> String?,
    private val fileManager: FileManager,
    private val androidSdk: AndroidSdk,
    private val simulators: AppleSimulators,
    private val environmentVariable: (String) -> String?,
    private val os: HostOs,
) {

    suspend operator fun invoke(project: String, repository: String, providers: RepositoryProviders): List<InstallationCheck> {
        val tools = buildList<Pair<ProviderToolId, suspend () -> Located>> {
            if (providers.gradle != null) add(ProviderToolId.GRADLE to { buildTool(repository, BuildTool.GRADLE) })
            if (providers.maven != null) add(ProviderToolId.MAVEN to { buildTool(repository, BuildTool.MAVEN) })
            if (providers.emulator != null) add(ProviderToolId.ANDROID_SDK to { androidSdk(repository) })
            if (providers.iosSimulator != null) add(ProviderToolId.IOS_SIMULATOR to ::iosSimulators)
            if (providers.xcode != null) add(ProviderToolId.XCODE to ::xcode)
            if (providers.xcode?.cocoaPods == true) add(ProviderToolId.COCOAPODS to { commandOnPath("pod") })
            if (providers.dockerCompose != null) add(ProviderToolId.DOCKER_COMPOSE to ::dockerCompose)
            if (providers.flutter != null) add(ProviderToolId.FLUTTER to { commandOnPath(flutterCommand(fileManager, repository).first()) })
        }
        return tools.flatMap { (tool, locate) ->
            val located = locateGuarded(locate)
            val dependent = located.then?.let { (next, locateNext) -> check(project, next, locateGuarded(locateNext)) }
            listOfNotNull(check(project, tool, located), dependent)
        }
    }

    private fun check(project: String, tool: ProviderToolId, located: Located): InstallationCheck {
        val finding = InstallationFinding.ProviderTool(project, tool, located.location, located.problem, located.installed, located.version)
        return InstallationCheck(if (located.problem == null) CheckState.PASSED else CheckState.WARNING, finding)
    }

    private suspend fun locateGuarded(locate: suspend () -> Located): Located = try {
        locate()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Located(null, ProviderToolProblem.Unusable(error.message))
    }

    fun unreadable(project: String, detail: String?): InstallationCheck = InstallationCheck(
        CheckState.WARNING,
        InstallationFinding.ProviderTool(project, ProviderToolId.PROVIDERS, null, ProviderToolProblem.ProvidersUnreadable(detail)),
    )

    private data class Located(
        val location: String?,
        val problem: ProviderToolProblem? = null,
        val installed: Int? = null,
        val version: String? = null,
        val then: Pair<ProviderToolId, suspend () -> Located>? = null,
    )

    private enum class BuildTool(
        val wrapper: String,
        val command: String,
        val wrapperProperties: String,
        val java: ProviderToolId,
        val distributionVersion: (String?) -> String?,
        val printedVersion: (String) -> String?,
        val minimumJava: (String) -> Int,
        val firstSupporting: (Int) -> String?,
    ) {
        GRADLE(
            "gradlew", "gradle", "gradle/wrapper/gradle-wrapper.properties", ProviderToolId.GRADLE_JAVA,
            ToolVersions::gradleDistribution, ToolVersions::gradlePrinted, JavaCompatibility::minimumJavaForGradle, JavaCompatibility::firstGradleFor,
        ),
        MAVEN(
            "mvnw", "mvn", ".mvn/wrapper/maven-wrapper.properties", ProviderToolId.MAVEN_JAVA,
            ToolVersions::mavenDistribution, ToolVersions::mavenPrinted, JavaCompatibility::minimumJavaForMaven, { null },
        ),
    }

    private suspend fun buildTool(repository: String, tool: BuildTool): Located {
        val wrapperPath = joinPath(repository, tool.wrapper)
        val (location, version) = if (fileManager.exists(wrapperPath)) {
            wrapperPath to tool.distributionVersion(fileManager.readProperties(joinPath(repository, tool.wrapperProperties))["distributionUrl"])
        } else {
            val command = findExecutable(tool.command) ?: return Located(null, ProviderToolProblem.NotFound(listOf(wrapperPath), listOf(tool.command)))
            command to printedVersion(command, tool.printedVersion)
        }
        return Located(location, version = version, then = tool.java to { java(tool, version) })
    }

    private suspend fun printedVersion(command: String, parse: (String) -> String?): String? = try {
        executor.executeWithin(listOf(command, "--version"), CommandTimeouts.LISTING).takeIf { it.isSuccess }?.let { parse(it.stdout) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun java(tool: BuildTool, toolVersion: String?): Located {
        val javaHome = environmentVariable(JAVA_HOME)?.takeIf { it.isNotBlank() }
        val java = if (javaHome != null) {
            javaUnder(javaHome).takeIf(fileManager::exists) ?: return Located(null, ProviderToolProblem.JavaHomeInvalid(javaHome))
        } else {
            findExecutable("java") ?: return Located(null, ProviderToolProblem.NotFound(listOf(JAVA_HOME), listOf("java")))
        }
        val result = executor.executeWithin(listOf(java, "-version"), CommandTimeouts.LISTING)
        if (!result.isSuccess) return Located(java, ProviderToolProblem.Unusable(output(result)))
        val major = ToolVersions.javaMajor(result.stderr + "\n" + result.stdout) ?: return Located(java, ProviderToolProblem.Unusable(output(result)))
        return Located(java, compatibility(tool, toolVersion, major), version = major.toString())
    }

    private fun javaUnder(javaHome: String): String =
        if (os.isWindows) "${javaHome.trimEnd('\\', '/')}\\bin\\java.exe" else joinPath(javaHome, "bin", "java")

    private fun compatibility(tool: BuildTool, toolVersion: String?, java: Int): ProviderToolProblem? {
        val version = toolVersion?.let(ToolVersions::numeric) ?: return null
        val required = tool.minimumJava(version)
        if (java < required) return ProviderToolProblem.JavaTooOld(java, required, toolVersion)
        val first = tool.firstSupporting(java) ?: return null
        return if (CheckInstallationUseCase.isAtLeast(version, first)) null else ProviderToolProblem.GradleTooOld(java, toolVersion, first)
    }

    private suspend fun dockerCompose(): Located = runs("docker", listOf("compose", "version")) { docker, output ->
        val version = ToolVersions.composePrinted(output)
        val tooOld = version?.takeUnless { CheckInstallationUseCase.isAtLeast(it, ToolVersions.MINIMUM_COMPOSE) }
        Located(
            docker,
            tooOld?.let { ProviderToolProblem.TooOld(it, ToolVersions.MINIMUM_COMPOSE) },
            version = version,
            then = ProviderToolId.DOCKER_DAEMON to { dockerDaemon(docker) },
        )
    }

    private suspend fun dockerDaemon(docker: String): Located {
        val result = executor.executeWithin(listOf(docker, "info", "--format", "{{.ServerVersion}}"), CommandTimeouts.LISTING)
        if (!result.isSuccess) return Located(null, ProviderToolProblem.DaemonNotRunning(os, output(result)))
        return Located(null, version = result.stdout.trim().ifEmpty { null })
    }

    private suspend fun androidSdk(repository: String): Located {
        val sdk = try {
            androidSdk.locate(repository)
        } catch (error: WorktreeError.AndroidSdkNotFound) {
            return Located(null, ProviderToolProblem.NotFound(error.checked))
        }
        val avds = androidSdk.listAvds(sdk).size
        return Located(sdk.root, if (avds == 0) ProviderToolProblem.NoAndroidVirtualDevice else null, avds)
    }

    private suspend fun iosSimulators(): Located {
        if (!simulators.isSupported()) return Located(null, ProviderToolProblem.NeedsMacOs)
        val runtimes = simulators.runtimes().count { it.platform.equals("iOS", ignoreCase = true) }
        return Located(null, if (runtimes == 0) ProviderToolProblem.NoIosRuntime else null, runtimes)
    }

    private suspend fun xcode(): Located =
        if (simulators.isSupported()) runs("xcodebuild", listOf("-version")) else Located(null, ProviderToolProblem.NeedsMacOs)

    private fun commandOnPath(command: String): Located {
        val location = findExecutable(command)
        return Located(location, if (location == null) ProviderToolProblem.NotFound(emptyList(), listOf(command)) else null)
    }

    private suspend fun runs(
        command: String,
        arguments: List<String>,
        succeeded: suspend (String, String) -> Located = { location, _ -> Located(location) },
    ): Located {
        val location = findExecutable(command) ?: return Located(null, ProviderToolProblem.NotFound(emptyList(), listOf(command)))
        val result = executor.executeWithin(listOf(location) + arguments, CommandTimeouts.LISTING)
        if (result.isSuccess) return succeeded(location, result.stdout)
        return Located(location, ProviderToolProblem.Unusable(output(result)))
    }

    private fun output(result: CommandResult): String =
        result.stderr.ifBlank { result.stdout }.trim().ifEmpty { "exit code ${result.exitCode}" }

    private companion object {
        const val JAVA_HOME = "JAVA_HOME"
    }
}
