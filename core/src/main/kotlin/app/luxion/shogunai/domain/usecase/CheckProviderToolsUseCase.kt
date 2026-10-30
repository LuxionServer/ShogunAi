package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.CheckState
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
) {

    suspend operator fun invoke(project: String, repository: String, providers: RepositoryProviders): List<InstallationCheck> {
        val tools = buildList<Pair<ProviderToolId, suspend () -> Located>> {
            if (providers.gradle != null) add(ProviderToolId.GRADLE to { wrapperOrCommand(repository, "gradlew", "gradle") })
            if (providers.maven != null) add(ProviderToolId.MAVEN to { wrapperOrCommand(repository, "mvnw", "mvn") })
            if (providers.emulator != null) add(ProviderToolId.ANDROID_SDK to { androidSdk(repository) })
            if (providers.iosSimulator != null) add(ProviderToolId.IOS_SIMULATOR to ::iosSimulators)
            if (providers.xcode != null) add(ProviderToolId.XCODE to ::xcode)
            if (providers.xcode?.cocoaPods == true) add(ProviderToolId.COCOAPODS to { commandOnPath("pod") })
            if (providers.dockerCompose != null) add(ProviderToolId.DOCKER_COMPOSE to { runs("docker", listOf("compose", "version")) })
            if (providers.flutter != null) add(ProviderToolId.FLUTTER to { commandOnPath(flutterCommand(fileManager, repository).first()) })
        }
        return tools.map { (tool, locate) ->
            val located = try {
                locate()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Located(null, ProviderToolProblem.Unusable(error.message))
            }
            val finding = InstallationFinding.ProviderTool(project, tool, located.location, located.problem, located.installed)
            InstallationCheck(if (located.problem == null) CheckState.PASSED else CheckState.WARNING, finding)
        }
    }

    fun unreadable(project: String, detail: String?): InstallationCheck = InstallationCheck(
        CheckState.WARNING,
        InstallationFinding.ProviderTool(project, ProviderToolId.PROVIDERS, null, ProviderToolProblem.ProvidersUnreadable(detail)),
    )

    private data class Located(val location: String?, val problem: ProviderToolProblem? = null, val installed: Int? = null)

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

    private fun wrapperOrCommand(repository: String, wrapper: String, command: String): Located {
        val wrapperPath = joinPath(repository, wrapper)
        val location = wrapperPath.takeIf(fileManager::exists) ?: findExecutable(command)
        return Located(location, notFound(location, listOf(wrapperPath), command))
    }

    private fun notFound(location: String?, searched: List<String>, command: String): ProviderToolProblem? =
        if (location == null) ProviderToolProblem.NotFound(searched, listOf(command)) else null

    private fun commandOnPath(command: String): Located {
        val location = findExecutable(command)
        return Located(location, notFound(location, emptyList(), command))
    }

    private suspend fun runs(command: String, arguments: List<String>): Located {
        val location = findExecutable(command) ?: return Located(null, ProviderToolProblem.NotFound(emptyList(), listOf(command)))
        val result = executor.executeWithin(listOf(location) + arguments, CommandTimeouts.LISTING)
        if (result.isSuccess) return Located(location)
        val output = result.stderr.ifBlank { result.stdout }.trim().ifEmpty { "exit code ${result.exitCode}" }
        return Located(location, ProviderToolProblem.Unusable(output))
    }
}
