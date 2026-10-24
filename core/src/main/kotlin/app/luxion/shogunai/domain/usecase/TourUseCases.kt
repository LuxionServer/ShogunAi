package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.INACTIVE_AFTER_DAYS_RANGE
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.joinPath
import java.util.UUID
import java.util.concurrent.TimeUnit

const val TOUR_PROJECT_NAME = "ShogunAi tour"

val tourAgents: List<AgentLaunchConfig> = listOf(
    AgentLaunchConfig(name = "Claude Code", agentCommand = "claude \"${TourScript.START_PROMPT}\"", useHeadroom = false),
    AgentLaunchConfig(name = "Codex", agentCommand = "codex \"${TourScript.START_PROMPT}\"", useHeadroom = false),
)

class CreateTourUseCase(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val projects: ProjectRepository,
    private val location: TourLocation = TourLocation(),
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(language: AppLanguage): Result<Project> = runCatching {
        if (fileManager.exists(location.root)) throw WorktreeError.TourAlreadyExists(location.root)
        try {
            SampleBuilder(language).build()
        } catch (error: Throwable) {
            runCatching { fileManager.deleteRecursively(location.root) }
            throw error
        }
        val project = Project(
            id = newId(),
            name = TOUR_PROJECT_NAME,
            config = ProjectConfig(
                baseRepositoryPath = location.repository,
                worktreesRoot = location.worktrees,
                secretFiles = emptyList(),
                agentLaunchConfig = tourAgents.first(),
                additionalAgents = tourAgents.drop(1),
                mainBranch = MAIN_BRANCH,
            ),
        )
        projects.save(project)
        project
    }

    private inner class SampleBuilder(private val language: AppLanguage) {
        private val repository = location.repository

        suspend fun build() {
            git(null, "init", "--bare", location.remote)
            git(null, "init", repository)
            git(repository, "symbolic-ref", "HEAD", "refs/heads/$MAIN_BRANCH")
            writeBaseFiles()
            commitAll(repository, "Start the sample project")
            git(repository, "remote", "add", "origin", location.remote)
            git(repository, "push", "--no-verify", "-u", "origin", MAIN_BRANCH)

            addWorktree("new-idea", "feature/new-idea")

            val needsYou = addWorktree("needs-you", "feature/needs-you")
            write(needsYou, "README.md", "$README\nA change nobody committed yet.\n")
            write(needsYou, "draft.md", "# Draft\n\nWork in progress.\n")

            val pushedWork = addWorktree("pushed-work", "feature/pushed-work")
            write(pushedWork, "feature.md", "# Pushed work\n\nCommitted and pushed, waiting for review.\n")
            commitAll(pushedWork, "Add the pushed feature")
            git(pushedWork, "push", "--no-verify", "-u", "origin", "feature/pushed-work")

            write(repository, "CHANGELOG.md", "# Changelog\n\n- Main moved on after pushed-work branched.\n")
            commitAll(repository, "Add a changelog")
            git(repository, "push", "--no-verify", "origin", MAIN_BRANCH)

            val mergedFix = addWorktree("merged-fix", "fix/merged-fix")
            write(mergedFix, "fix.md", "# Fix\n\nAlready merged into main.\n")
            commitAll(mergedFix, "Fix a typo")
            git(mergedFix, "push", "--no-verify", "-u", "origin", "fix/merged-fix")
            git(repository, "merge", "--no-verify", "--no-ff", "-m", "Merge fix/merged-fix", "fix/merged-fix")
            git(repository, "push", "--no-verify", "origin", MAIN_BRANCH)

            val oldExperiment = addWorktree("old-experiment", "feature/old-experiment")
            write(oldExperiment, "experiment.md", "# Experiment\n\nNobody touched this in months.\n")
            commitAll(oldExperiment, "Try an old idea", environment = datedEnvironment(INACTIVE_DAYS))
            git(oldExperiment, "push", "--no-verify", "-u", "origin", "feature/old-experiment")

            val locked = addWorktree("locked-task", "feature/locked-task")
            git(repository, "worktree", "lock", "--reason", LOCK_REASON, locked)

            val gone = addWorktree("folder-gone", "feature/folder-gone")
            fileManager.deleteRecursively(gone)
        }

        private fun writeBaseFiles() {
            write(repository, "README.md", README)
            write(repository, ".gitignore", ".env.local\n.env.shogunai\n")
            write(repository, ".env.local", "GREETING=hello from the base repository\n")
            write(repository, ".shogunai.json", REPOSITORY_CONFIG)
            write(repository, "AGENTS.md", TourScript.agentInstructions(language, location))
            write(repository, "CLAUDE.md", TourScript.claudeInstructions)
        }

        private suspend fun addWorktree(name: String, branch: String): String {
            val path = location.worktree(name)
            git(repository, "worktree", "add", "-b", branch, path, MAIN_BRANCH)
            return path
        }

        private suspend fun commitAll(directory: String, message: String, environment: Map<String, String> = emptyMap()) {
            git(directory, "add", "-A")
            git(directory, "commit", "--no-verify", "-m", message, environment = environment)
        }

        private fun write(directory: String, name: String, text: String) = fileManager.writeText(joinPath(directory, name), text)

        private fun datedEnvironment(daysAgo: Int): Map<String, String> {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(now()) - TimeUnit.DAYS.toSeconds(daysAgo.toLong())
            val date = "$seconds +0000"
            return mapOf("GIT_AUTHOR_DATE" to date, "GIT_COMMITTER_DATE" to date)
        }

        private suspend fun git(directory: String?, vararg arguments: String, environment: Map<String, String> = emptyMap()) {
            val command = listOf("git") + GIT_OPTIONS + arguments
            val result = try {
                executor.executeWithEnvironment(command, environment, directory)
            } catch (error: CommandExecutionException) {
                throw WorktreeError.TourCreationFailed(command, error.cause?.message ?: error.message.orEmpty())
            }
            if (!result.isSuccess) throw WorktreeError.TourCreationFailed(command, result.stderr.ifBlank { result.stdout })
        }
    }

    private companion object {
        const val MAIN_BRANCH = "main"
        const val LOCK_REASON = "Locked by the ShogunAi tour"
        val INACTIVE_DAYS = INACTIVE_AFTER_DAYS_RANGE.last + 30
        val GIT_OPTIONS = listOf(
            "-c", "user.name=ShogunAi",
            "-c", "user.email=tour@shogunai.invalid",
            "-c", "commit.gpgsign=false",
        )
        val README = """
            |# Sample project
            |
            |A throwaway repository created by ShogunAi's tour. Ask your agent to "${TourScript.START_PROMPT}".
            |""".trimMargin()
        val REPOSITORY_CONFIG = """
            |{
            |  "copy": [".env.local"],
            |  "env": {
            |    "PORT": "${'$'}{port:web}",
            |    "APP_URL": "http://localhost:${'$'}{port:web}"
            |  },
            |  "envFile": ".env.shogunai",
            |  "hooks": { "postCreate": [["git", "status", "--short"]] }
            |}
            |""".trimMargin()
    }
}

class EndTourUseCase(
    private val fileManager: FileManager,
    private val projects: ProjectRepository,
    private val location: TourLocation = TourLocation(),
) {
    operator fun invoke(): Result<Unit> = runCatching {
        projects.list().filter(location::isTour).forEach { projects.delete(it.id) }
        fileManager.deleteRecursively(location.root)
    }
}
