package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.HostPath
import app.luxion.shogunai.domain.io.HostPathSource
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.AgentToolId
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.InstallationReport
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.CancellationException

class CheckInstallationUseCase(
    private val executor: ShellCommandExecutor,
    private val findExecutable: (String) -> String?,
    private val path: () -> HostPath,
    private val shell: String?,
    private val commandLineTool: suspend () -> CommandLineToolStatus,
    private val hooks: ClaudeCodeHooks,
    private val hooksSettingsPath: String,
    private val herdrLocation: () -> String?,
    private val orcaLocation: () -> String?,
    private val projects: ProjectRepository,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
    private val providerTools: CheckProviderToolsUseCase,
    private val multirepos: CheckMultireposUseCase,
) {

    suspend operator fun invoke(): InstallationReport {
        val git = guarded(::gitFailure) { git() }
        val pathSource = guarded({ InstallationCheck(CheckState.FAILED, InstallationFinding.PathSource(HostPathSource.FALLBACK, shell)) }) { pathSource() }
        val status = runCatchingNonCancellation { commandLineTool() }
        val shogun = status.fold(::shogunCheck) { InstallationCheck(CheckState.FAILED, InstallationFinding.ShogunCommand(CommandLineToolState.MISSING, null, null, false)) }
        val hooksCheck = guarded(::hooksFailure) { hooks(status.getOrNull()) }
        val tools = listOf(
            agentTool(AgentToolId.CLAUDE_CODE) { findExecutable("claude") },
            agentTool(AgentToolId.HERDR, herdrLocation),
            agentTool(AgentToolId.ORCA, orcaLocation),
        )
        val projectChecks = runCatchingNonCancellation { projects.list() }.getOrDefault(emptyList()).flatMap { project ->
            listOf(guarded({ projectFailure(project, it) }) { projectFiles(project) }) + projectProviders(project)
        }
        val multirepoChecks = runCatchingNonCancellation { multirepos() }.getOrDefault(emptyList())
        return InstallationReport(listOf(git, pathSource, shogun, hooksCheck) + tools + projectChecks + multirepoChecks)
    }

    private suspend fun git(): InstallationCheck {
        val location = findExecutable("git") ?: return InstallationCheck(CheckState.FAILED, InstallationFinding.Git(null, null, MINIMUM_GIT))
        val result = try {
            executor.executeWithin(listOf(location, "--version"), CommandTimeouts.LISTING)
        } catch (error: CommandExecutionException) {
            return InstallationCheck(CheckState.FAILED, InstallationFinding.Git(location, null, MINIMUM_GIT, error.cause.message ?: error.message))
        }
        if (!result.isSuccess) return InstallationCheck(CheckState.FAILED, InstallationFinding.Git(location, null, MINIMUM_GIT))
        val version = gitVersion(result.stdout)
            ?: return InstallationCheck(CheckState.FAILED, InstallationFinding.Git(location, null, MINIMUM_GIT, result.stdout.trim()))
        val state = if (isAtLeast(version, MINIMUM_GIT)) CheckState.PASSED else CheckState.FAILED
        return InstallationCheck(state, InstallationFinding.Git(location, version, MINIMUM_GIT))
    }

    private fun gitFailure(error: Throwable) =
        InstallationCheck(CheckState.FAILED, InstallationFinding.Git(null, null, MINIMUM_GIT, error.message))

    private fun pathSource(): InstallationCheck {
        val source = path().source
        val state = if (source == HostPathSource.FALLBACK) CheckState.WARNING else CheckState.PASSED
        return InstallationCheck(state, InstallationFinding.PathSource(source, shell))
    }

    private fun shogunCheck(status: CommandLineToolStatus): InstallationCheck {
        val state = when (status.state) {
            CommandLineToolState.AVAILABLE -> CheckState.PASSED
            CommandLineToolState.INSTALLED_NOT_ON_PATH -> CheckState.WARNING
            CommandLineToolState.BROKEN, CommandLineToolState.MISSING -> CheckState.FAILED
        }
        return InstallationCheck(state, InstallationFinding.ShogunCommand(status.state, status.launcher, status.folder, status.restartNeeded))
    }

    private fun hooks(status: CommandLineToolStatus?): InstallationCheck {
        val hooksStatus = try {
            hooks.status(hooksSettingsPath).getOrThrow()
        } catch (error: WorktreeError.InvalidClaudeSettings) {
            return InstallationCheck(CheckState.FAILED, InstallationFinding.Hooks(hooksSettingsPath, null, invalidDetail = error.detail))
        }
        val shogun = if (status != null) status.state else CommandLineToolState.MISSING
        val finding = InstallationFinding.Hooks(hooksSettingsPath, hooksStatus.state, hooksStatus.events, shogun)
        val state = when {
            hooksStatus.state != HooksInstallState.INSTALLED -> CheckState.WARNING
            status?.usableByHooks == true -> CheckState.PASSED
            shogun == CommandLineToolState.INSTALLED_NOT_ON_PATH -> CheckState.WARNING
            else -> CheckState.FAILED
        }
        return InstallationCheck(state, finding)
    }

    private fun hooksFailure(error: Throwable) =
        InstallationCheck(CheckState.FAILED, InstallationFinding.Hooks(hooksSettingsPath, null, invalidDetail = error.message))

    private suspend fun agentTool(tool: AgentToolId, locate: () -> String?): InstallationCheck {
        val location = runCatchingNonCancellation { locate() }.getOrNull()
        return InstallationCheck(if (location == null) CheckState.NOT_FOUND else CheckState.PASSED, InstallationFinding.AgentTool(tool, location))
    }

    private suspend fun projectFiles(project: Project): InstallationCheck {
        val config = project.config
        if (!fileManager.isDirectory(config.baseRepositoryPath)) {
            return InstallationCheck(CheckState.FAILED, InstallationFinding.ProjectFiles(project.name, config.baseRepositoryPath, true, 0, emptyList()))
        }
        val check = checkLocalFiles(config, executor, fileManager, provisioningSource)
        val state = if (check.unavailable.isEmpty()) CheckState.PASSED else CheckState.FAILED
        return InstallationCheck(state, InstallationFinding.ProjectFiles(project.name, config.baseRepositoryPath, false, check.checked, check.unavailable))
    }

    private suspend fun projectProviders(project: Project): List<InstallationCheck> {
        val repository = project.config.baseRepositoryPath
        if (!fileManager.isDirectory(repository)) return emptyList()
        val providers = runCatchingNonCancellation { provisioningSource.read(repository).providers }
            .getOrElse { return listOf(providerTools.unreadable(project.name, it.message)) }
        return providerTools(project.name, repository, providers)
    }

    private fun projectFailure(project: Project, error: Throwable) = InstallationCheck(
        CheckState.FAILED,
        InstallationFinding.ProjectFiles(project.name, project.config.baseRepositoryPath, false, 0, emptyList(), error.message ?: error::class.simpleName),
    )

    private suspend fun guarded(onError: (Throwable) -> InstallationCheck, check: suspend () -> InstallationCheck): InstallationCheck =
        runCatchingNonCancellation { check() }.getOrElse(onError)

    private suspend fun <T> runCatchingNonCancellation(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    companion object {
        const val MINIMUM_GIT = "2.29"
        private val GIT_VERSION = Regex("git version (\\d+)\\.(\\d+)(?:\\.(\\d+))?")

        fun gitVersion(output: String): String? =
            GIT_VERSION.find(output)?.let { match -> match.groupValues.drop(1).filter { it.isNotEmpty() }.joinToString(".") }

        fun isAtLeast(version: String, minimum: String): Boolean {
            val actual = version.split('.').map { it.toInt() }
            val wanted = minimum.split('.').map { it.toInt() }
            val compared = actual.zip(wanted).map { (a, w) -> a.compareTo(w) }.firstOrNull { it != 0 }
            return if (compared != null) compared > 0 else actual.size >= wanted.size
        }
    }
}
