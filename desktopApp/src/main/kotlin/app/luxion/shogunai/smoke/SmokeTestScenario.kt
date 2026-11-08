package app.luxion.shogunai.smoke

import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.domain.usecase.CommandLineToolStatus
import app.luxion.shogunai.domain.usecase.HooksInstallState
import app.luxion.shogunai.ui.projectconfig.ProjectConfigViewModel
import app.luxion.shogunai.ui.projectconfig.SaveOutcome
import app.luxion.shogunai.ui.shell.ClaudeHooksState
import app.luxion.shogunai.ui.shell.TourState
import app.luxion.shogunai.ui.shell.TourStep
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SmokeTestScenario(
    private val hooks: ClaudeHooksState,
    private val tour: TourState,
    private val projectForm: () -> ProjectConfigViewModel,
    private val declaredCopies: (repositoryPath: String) -> List<String>,
    private val createWorktree: suspend (config: ProjectConfig, taskId: String) -> Result<Worktree>,
) {
    private val steps = mutableListOf<SmokeStep>()
    private var tourReached: TourStep? = null
    private var tourToolStepShown = false
    private var worktreePath: String? = null
    private var copiedFiles = emptyList<String>()

    suspend fun run(repository: String?, localFiles: List<String>): SmokeTestReport {
        readCommand()
        installCommand()
        installHooks()
        walkTour()
        val project = addProject(repository, localFiles)
        val worktree = project?.let { createWorktree(it) }
        if (project != null && worktree != null) checkFiles(project.config, worktree) else skip(SmokeStepName.CHECK_FILES, "no worktree")
        return SmokeTestReport(
            steps = steps.toList(),
            commandLineTool = hooks.toolStatus,
            hooksState = hooks.status?.state,
            tourReached = tourReached,
            tourToolStepShown = tourToolStepShown,
            worktreePath = worktreePath,
            copiedFiles = copiedFiles,
        )
    }

    private suspend fun readCommand() {
        hooks.refresh()
        val status = hooks.toolStatus ?: return fail(SmokeStepName.READ_COMMAND, "the command-line tool's state couldn't be read")
        pass(SmokeStepName.READ_COMMAND, describe(status))
    }

    private suspend fun installCommand() {
        val status = hooks.toolStatus ?: return skip(SmokeStepName.INSTALL_COMMAND, "state unknown")
        val offered = hooks.toolNeedsAttention && status.canInstall && status.state != CommandLineToolState.INSTALLED_NOT_ON_PATH
        if (!offered) return skip(SmokeStepName.INSTALL_COMMAND, "not offered: ${describe(status)}")
        hooks.installTool()
        val error = hooks.toolError
        val installed = hooks.toolStatus
        when {
            error != null -> fail(SmokeStepName.INSTALL_COMMAND, error.messageOrName())
            installed == null -> fail(SmokeStepName.INSTALL_COMMAND, "the command-line tool's state couldn't be read after installing")
            else -> pass(SmokeStepName.INSTALL_COMMAND, describe(installed))
        }
    }

    private fun installHooks() {
        if (!hooks.canChangeHooks) return skip(SmokeStepName.INSTALL_HOOKS, "not offered: the command isn't usable by the hooks")
        hooks.error?.let { return fail(SmokeStepName.INSTALL_HOOKS, it.messageOrName()) }
        if (hooks.status?.state == HooksInstallState.INSTALLED) return pass(SmokeStepName.INSTALL_HOOKS, "already installed in ${hooks.status?.path}")
        val action = hooks.installAction ?: return fail(SmokeStepName.INSTALL_HOOKS, "the hooks' state couldn't be read")
        hooks.request(action)
        val pending = hooks.pending ?: return fail(SmokeStepName.INSTALL_HOOKS, hooks.error?.messageOrName() ?: "no change was offered")
        hooks.confirm()
        hooks.error?.let { return fail(SmokeStepName.INSTALL_HOOKS, it.messageOrName()) }
        if (hooks.status?.state == HooksInstallState.INSTALLED) pass(SmokeStepName.INSTALL_HOOKS, "installed in ${pending.path}")
        else fail(SmokeStepName.INSTALL_HOOKS, "the hooks read as ${hooks.status?.state} after installing in ${pending.path}")
    }

    private suspend fun walkTour() {
        tour.start()
        if (tour.step == TourStep.EXISTING) tour.restart() else tour.continueFromWelcome()
        if (tour.step == TourStep.TOOL) {
            tourToolStepShown = true
            tour.continueFromTool()
        }
        tourReached = tour.step
        tour.error?.let { return fail(SmokeStepName.TOUR, it.messageOrName()) }
        if (tour.step != TourStep.OPEN_AGENT) return fail(SmokeStepName.TOUR, "the tour stopped at ${tour.step}")
        val project = tour.project?.config?.baseRepositoryPath
        tour.confirmEnd().onFailure { return fail(SmokeStepName.TOUR, "the tour started but couldn't be ended: ${it.messageOrName()}") }
        tour.close()
        pass(SmokeStepName.TOUR, "reached ${TourStep.OPEN_AGENT} with its sample in $project, then ended")
    }

    private suspend fun addProject(repository: String?, localFiles: List<String>): Project? {
        if (repository.isNullOrBlank()) {
            fail(SmokeStepName.ADD_PROJECT, "$REPOSITORY_VARIABLE isn't set")
            skip(SmokeStepName.CREATE_WORKTREE, "no project")
            return null
        }
        val form = projectForm()
        form.chooseFolder(repository).onFailure {
            fail(SmokeStepName.ADD_PROJECT, "$repository isn't a Git repository: ${it.messageOrName()}")
            skip(SmokeStepName.CREATE_WORKTREE, "no project")
            return null
        }
        localFiles.forEach(form::addSecretFile)
        val outcome = form.submit().let { if (it is SaveOutcome.NeedsPreview) form.submit(previewConfirmed = true) else it }
        if (outcome !is SaveOutcome.Saved) {
            fail(SmokeStepName.ADD_PROJECT, "the project for $repository couldn't be saved: ${describe(outcome)}")
            skip(SmokeStepName.CREATE_WORKTREE, "no project")
            return null
        }
        pass(SmokeStepName.ADD_PROJECT, "${outcome.project.name} with local files ${outcome.project.config.secretFiles}")
        return outcome.project
    }

    private suspend fun createWorktree(project: Project): Worktree? =
        createWorktree(project.config, WORKTREE_TASK_ID).fold(
            onSuccess = { worktree ->
                worktreePath = worktree.path
                pass(SmokeStepName.CREATE_WORKTREE, worktree.path)
                worktree
            },
            onFailure = {
                fail(SmokeStepName.CREATE_WORKTREE, it.messageOrName())
                null
            },
        )

    private fun checkFiles(config: ProjectConfig, worktree: Worktree) {
        val expected = (config.secretFiles + declaredCopies(config.baseRepositoryPath)).filterNot(::isPattern).distinct()
        if (expected.isEmpty()) return fail(SmokeStepName.CHECK_FILES, "no local file was declared to check")
        val base = Paths.get(config.baseRepositoryPath)
        val copy = Paths.get(worktree.path)
        val problems = expected.mapNotNull { entry -> problemWith(entry, base.resolve(entry), copy.resolve(entry)) }
        if (problems.isNotEmpty()) return fail(SmokeStepName.CHECK_FILES, problems.joinToString("; "))
        copiedFiles = expected
        pass(SmokeStepName.CHECK_FILES, expected.joinToString(", "))
    }

    private fun problemWith(entry: String, original: Path, copy: Path): String? = when {
        !Files.exists(original) -> "$entry is missing from the base repository"
        !Files.exists(copy) -> "$entry wasn't copied into ${copy.parent}"
        Files.isDirectory(original) -> null
        !Files.readAllBytes(original).contentEquals(Files.readAllBytes(copy)) -> "$entry differs from the base repository's"
        else -> null
    }

    private fun pass(name: SmokeStepName, detail: String) {
        steps += SmokeStep(name, SmokeStepStatus.PASSED, detail)
    }

    private fun fail(name: SmokeStepName, detail: String) {
        steps += SmokeStep(name, SmokeStepStatus.FAILED, detail)
    }

    private fun skip(name: SmokeStepName, detail: String) {
        steps += SmokeStep(name, SmokeStepStatus.SKIPPED, detail)
    }

    private fun describe(status: CommandLineToolStatus): String = buildString {
        append(status.state.name)
        status.launcher?.let { append(" at ").append(it) }
        if (status.restartNeeded) append(", restart needed")
    }

    private fun describe(outcome: SaveOutcome): String = when (outcome) {
        is SaveOutcome.Failed -> outcome.error.messageOrName()
        is SaveOutcome.Invalid -> "invalid ${outcome.firstInvalidField}"
        is SaveOutcome.InvalidRepositoryConfig -> "invalid repository setting ${outcome.field}"
        is SaveOutcome.NeedsPreview -> "the repository change wasn't confirmed"
        is SaveOutcome.Saved -> "saved"
    }

    private fun isPattern(entry: String): Boolean = entry.any { it in PATTERN_CHARACTERS }

    private fun Throwable.messageOrName(): String = message ?: this::class.simpleName.orEmpty()

    companion object {
        const val REPOSITORY_VARIABLE = "SHOGUNAI_SMOKE_TEST_REPOSITORY"
        const val WORKTREE_TASK_ID = "smoke-app"
        private const val PATTERN_CHARACTERS = "*?["
    }
}
