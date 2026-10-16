package app.luxion.shogunai.cli

import app.luxion.shogunai.BuildInfo
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DeleteMergedBranchUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ProvisionWorktreeUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.ResolveRepositoryUseCase
import app.luxion.shogunai.domain.usecase.classifyOrigin
import app.luxion.shogunai.domain.usecase.normalizeTaskId
import app.luxion.shogunai.domain.usecase.selectCleanupCandidates
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class ShogunCli(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val projectRepository: ProjectRepository,
    private val provisioningSource: RepositoryProvisioningSource,
    private val workingDirectory: String,
    private val out: Appendable,
    private val err: Appendable,
    private val readInput: () -> String = { "" },
    private val canonicalPath: (String) -> String = { File(it).canonicalPath },
) {
    fun run(args: List<String>): Int = when (val parsed = parseArguments(args)) {
        is ParsedArguments.UsageError -> {
            err.line("shogun: ${parsed.message}")
            err.line("Run 'shogun help' for usage.")
            EXIT_USAGE
        }
        is ParsedArguments.Command -> runBlocking { execute(parsed.command) }
    }

    private suspend fun execute(command: CliCommand): Int = try {
        when (command) {
            CliCommand.Help -> out.append(USAGE).let { EXIT_OK }
            CliCommand.Version -> out.line("shogun ${BuildInfo.VERSION}").let { EXIT_OK }
            is CliCommand.New -> new(command)
            is CliCommand.Provision -> provision(command)
            CliCommand.Status -> status()
            is CliCommand.Clean -> clean(command)
            CliCommand.HookWorktreeCreate -> hookWorktreeCreate()
            CliCommand.HookWorktreeRemove -> hookWorktreeRemove()
        }
    } catch (error: WorktreeError) {
        err.line("shogun: ${error.message}")
        EXIT_FAILURE
    } catch (error: CommandExecutionException) {
        err.line("shogun: ${error.message}")
        EXIT_FAILURE
    }

    private suspend fun new(command: CliCommand.New): Int {
        val config = configFor(workingDirectory, worktreesRoot = command.worktreesRoot?.let(::absolute))
        if (config.worktreesRoot.isBlank()) {
            err.line("shogun: this repository has no ShogunAi project, so there's no worktrees folder.")
            err.line("Pass --worktrees-root <dir>, or add the repository as a project in the desktop app.")
            return EXIT_USAGE
        }
        val worktree = if (command.branch != null) {
            CreateWorktreeFromBranchUseCase(config, executor, fileManager, provisioningSource)(command.branch)
        } else {
            CreateWorktreeUseCase(config, executor, fileManager, provisioningSource)(command.taskId.orEmpty(), command.branchType)
        }.getOrThrow()
        out.line(worktree.path)
        return EXIT_OK
    }

    private suspend fun provision(command: CliCommand.Provision): Int {
        val target = absolute(command.path ?: workingDirectory)
        val config = configFor(target)
        val worktree = ListWorktreesUseCase(config, executor)().getOrThrow()
            .firstOrNull { sameDirectory(it.path, target) }
            ?: throw WorktreeError.NotAWorktree(target)
        ProvisionWorktreeUseCase(config, executor, fileManager, provisioningSource)(worktree.path).getOrThrow()
            .forEach { out.line(it) }
        return EXIT_OK
    }

    private suspend fun status(): Int {
        val config = configFor(workingDirectory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val mainBranch = ResolveMainBranchUseCase(config, executor)().getOrNull()
        val readStatus = ReadWorktreeStatusUseCase(executor)
        worktrees.forEach { worktree ->
            val status = if (worktree.isPrunable) null else readStatus(worktree, mainBranch).getOrNull()
            out.line(statusLine(worktree, status, config))
        }
        return EXIT_OK
    }

    private suspend fun clean(command: CliCommand.Clean): Int {
        val config = configFor(workingDirectory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val mainBranch = ResolveMainBranchUseCase(config, executor)().getOrNull()
        val readStatus = ReadWorktreeStatusUseCase(executor)
        val statuses = worktrees
            .filterNot { it.isMain || it.isPrunable || it.isLocked }
            .mapNotNull { worktree -> readStatus(worktree, mainBranch).getOrNull()?.let { worktree.path to it } }
            .toMap()
        val candidates = selectCleanupCandidates(worktrees, statuses)
        if (candidates.isEmpty()) {
            out.line("Nothing to clean up.")
            return EXIT_OK
        }
        if (!command.confirmed) {
            candidates.forEach { out.line("would remove ${it.path}${if (it.isPrunable) " (folder missing)" else ""}") }
            out.line("Run 'shogun clean --yes' to remove ${if (candidates.size == 1) "it" else "them"}.")
            return EXIT_OK
        }
        val report = CleanUpWorktreesUseCase(config, executor)(candidates, mainBranch, command.deleteBranches).getOrThrow()
        report.removed.forEach { out.line("removed $it") }
        report.keptBranches.forEach { out.line("kept branch $it (not merged)") }
        report.failures.forEach { err.line("shogun: couldn't remove ${it.path}: ${it.error.message}") }
        return if (report.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
    }

    private suspend fun hookWorktreeCreate(): Int {
        val input = hookInput("worktree-create") ?: return EXIT_USAGE
        val name = input.string("name") ?: return hookUsageError("worktree-create", "the input has no \"name\"")
        val directory = input.string("cwd") ?: workingDirectory
        val projectConfig = configFor(directory)
        val config = if (projectConfig.worktreesRoot.isBlank()) {
            projectConfig.copy(worktreesRoot = File(projectConfig.baseRepositoryPath, CLAUDE_CODE_WORKTREES).path)
        } else {
            projectConfig
        }
        val target = config.worktreePathFor(normalizeTaskId(name))
        val existing = ListWorktreesUseCase(config, executor)().getOrThrow()
            .firstOrNull { !it.isMain && sameDirectory(it.path, target) }
        val path = existing?.path
            ?: CreateWorktreeUseCase(config, executor, fileManager, provisioningSource)(name, BranchType.FEATURE).getOrThrow().path
        out.line(path)
        return EXIT_OK
    }

    private suspend fun hookWorktreeRemove(): Int {
        val input = hookInput("worktree-remove") ?: return EXIT_USAGE
        val path = input.string("worktree_path")
            ?: return hookUsageError("worktree-remove", "the input has no \"worktree_path\"")
        val directory = if (fileManager.exists(path)) path else input.string("cwd") ?: workingDirectory
        val config = configFor(directory)
        val worktree = ListWorktreesUseCase(config, executor)().getOrThrow()
            .firstOrNull { !it.isMain && sameDirectory(it.path, path) }
            ?: throw WorktreeError.NotAWorktree(path)
        RemoveWorktreeUseCase(config, executor)(worktree.path, force = true).getOrThrow()
        worktree.branch?.let { branch ->
            if (!DeleteMergedBranchUseCase(config, executor)(branch).getOrThrow()) {
                err.line("shogun: kept branch $branch because it isn't merged")
            }
        }
        return EXIT_OK
    }

    private fun hookInput(event: String): JsonObject? {
        val text = readInput()
        val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        if (parsed == null) hookUsageError(event, "expected the hook's JSON on standard input")
        return parsed
    }

    private fun hookUsageError(event: String, message: String): Int {
        err.line("shogun: hook $event: $message")
        return EXIT_USAGE
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private suspend fun configFor(directory: String, worktreesRoot: String? = null): ProjectConfig {
        val repository = ResolveRepositoryUseCase(executor)(directory).getOrThrow()
        val project = projectRepository.list().firstOrNull { sameDirectory(it.config.baseRepositoryPath, repository) }
        val config = project?.config ?: ProjectConfig(baseRepositoryPath = repository, worktreesRoot = "", secretFiles = emptyList())
        return if (worktreesRoot != null) config.copy(worktreesRoot = worktreesRoot) else config
    }

    private fun statusLine(worktree: Worktree, status: WorktreeStatus?, config: ProjectConfig): String {
        val details = buildList {
            add(worktree.branch ?: "(detached)")
            if (worktree.isMain) add("main")
            classifyOrigin(worktree, config)?.let { add("origin=${it.label}") }
            if (worktree.isLocked) add("locked")
            when {
                worktree.isPrunable -> add("folder-missing")
                status == null -> add("status-unavailable")
                else -> {
                    add("changes=${status.uncommittedChanges}")
                    status.upstream?.let { add("ahead=${it.ahead} behind=${it.behind}") }
                    if (status.isMerged) add("merged")
                }
            }
        }
        return (listOf(worktree.path) + details).joinToString("  ")
    }

    private fun absolute(path: String): String =
        if (File(path).isAbsolute) path else File(workingDirectory, path).toPath().normalize().toString()

    private fun sameDirectory(first: String, second: String): Boolean =
        runCatching { canonicalPath(first) == canonicalPath(second) }.getOrDefault(first == second)

    private fun Appendable.line(text: String) {
        append(text).append('\n')
    }

    private val WorktreeOrigin.label: String
        get() = when (this) {
            WorktreeOrigin.SHOGUNAI -> "shogunai"
            WorktreeOrigin.CLAUDE_CODE -> "claude-code"
            WorktreeOrigin.OTHER -> "other"
        }

    companion object {
        const val EXIT_OK = 0
        const val EXIT_FAILURE = 1
        const val EXIT_USAGE = 2
        private const val CLAUDE_CODE_WORKTREES = ".claude/worktrees"

        val USAGE = """
            Usage: shogun <command> [options]

            Commands:
              new <task-id> [--fix]       Create a worktree on a new feature/ (or fix/) branch and print its path
              new --branch <branch>       Create a worktree for an existing local branch and print its path
                  --worktrees-root <dir>  Folder for the worktree when the repository has no ShogunAi project
              provision [<path>]          Copy the declared files into an existing worktree (default: current directory)
              status                      List the repository's worktrees with their state
              clean [--yes] [--delete-branches]
                                          List merged or missing worktrees; remove them with --yes
              hook worktree-create        Claude Code WorktreeCreate hook: create and provision the worktree, print its path
              hook worktree-remove        Claude Code WorktreeRemove hook: remove the worktree, delete its branch if merged
              help                        Show this help
              --version                   Show the version

            Works on the Git repository containing the current directory. Files to copy come from the
            repository's .shogunai.json and .worktreeinclude, and from the desktop app's project if there is one.

        """.trimIndent() + "\n"
    }
}
