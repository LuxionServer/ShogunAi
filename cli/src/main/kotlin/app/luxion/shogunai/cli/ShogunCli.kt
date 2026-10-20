package app.luxion.shogunai.cli

import app.luxion.shogunai.BuildInfo
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.GradleDaemonSource
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.io.HerdrConfigSource
import app.luxion.shogunai.domain.io.KmpTestWiring
import app.luxion.shogunai.domain.io.PortProbe
import app.luxion.shogunai.domain.io.PostCreateApproval
import app.luxion.shogunai.domain.io.PostCreateDecision
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.model.ANDROID_SERIAL
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.DaemonKeptReason
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.GradleDaemon
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_UDID
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.ProvisionDriftState
import app.luxion.shogunai.domain.model.ProvisionMode
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.ProvisionedFile
import app.luxion.shogunai.domain.model.ShellQuoting
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.model.XcodeProvider
import app.luxion.shogunai.domain.usecase.BranchDeletion
import app.luxion.shogunai.domain.usecase.CheckGradleBuildCacheUseCase
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.CloseHerdrWorkspacesUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DeleteMergedBranchUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.DerivedDataMeasure
import app.luxion.shogunai.domain.usecase.DeleteSimulatorsUseCase
import app.luxion.shogunai.domain.usecase.EnvironmentFormat
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.FetchRemoteUseCase
import app.luxion.shogunai.domain.usecase.findWorktreeTarget
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.MachineProviderContributions
import app.luxion.shogunai.domain.usecase.OpenWorktreeInHerdrUseCase
import app.luxion.shogunai.domain.usecase.effectiveGradleJvmArgs
import app.luxion.shogunai.domain.usecase.PrepareWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.usecase.ProviderContributions
import app.luxion.shogunai.domain.usecase.CheckProvisionedFilesUseCase
import app.luxion.shogunai.domain.usecase.ProvisionWorktreeUseCase
import app.luxion.shogunai.domain.usecase.ReadEmulatorStatesUseCase
import app.luxion.shogunai.domain.usecase.ReadSimulatorStatesUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.ReleaseWorktreeResourcesUseCase
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.GitFailureCause
import app.luxion.shogunai.domain.usecase.resolveWorktreeTarget
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.herdrDetail
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.ResolveRepositoryUseCase
import app.luxion.shogunai.domain.usecase.ResolveStartPointUseCase
import app.luxion.shogunai.domain.usecase.ResolveWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.usecase.StartWorktreeEmulatorUseCase
import app.luxion.shogunai.domain.usecase.StartWorktreeSimulatorUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.StopWorktreeEmulatorUseCase
import app.luxion.shogunai.domain.usecase.StopWorktreeSimulatorUseCase
import app.luxion.shogunai.domain.usecase.WriteEnvironmentFileUseCase
import app.luxion.shogunai.domain.usecase.classifyOrigin
import app.luxion.shogunai.domain.usecase.formatEnvironment
import app.luxion.shogunai.domain.usecase.idleDaemonsOfRemovedWorktrees
import app.luxion.shogunai.domain.usecase.normalizeTaskId
import app.luxion.shogunai.domain.usecase.owningWorktree
import app.luxion.shogunai.domain.usecase.classifyGitFailure
import app.luxion.shogunai.domain.usecase.hint
import app.luxion.shogunai.domain.usecase.selectCleanup
import app.luxion.shogunai.domain.usecase.totalResidentMemory
import app.luxion.shogunai.infrastructure.CliHerdrClient
import app.luxion.shogunai.infrastructure.FileKmpTestWiring
import java.io.File
import java.io.FileNotFoundException
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ShogunCli(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val projectRepository: ProjectRepository,
    private val provisioningSource: RepositoryProvisioningSource,
    private val daemonSource: GradleDaemonSource,
    private val gradleUserHome: String,
    private val environmentStateStore: EnvironmentStateStore,
    private val portProbe: PortProbe,
    private val androidSdk: AndroidSdk,
    private val appleSimulators: AppleSimulators,
    private val kmpTestWiring: KmpTestWiring,
    private val derivedDataSource: DerivedDataSource,
    private val herdrConfig: HerdrConfigSource,
    private val workingDirectory: String,
    private val out: Appendable,
    private val err: Appendable,
    private val readInput: () -> String = { "" },
    private val canonicalPath: (String) -> String = { File(it).canonicalPath },
    private val now: () -> Instant = Instant::now,
    private val environmentVariable: (String) -> String? = System::getenv,
    private val userHome: String = System.getProperty("user.home"),
    private val herdr: HerdrClient = CliHerdrClient(executor, userHome = userHome),
    private val trust: RepositoryTrust = RepositoryTrust.NONE,
    private val readAnswer: () -> String? = { null },
) {
    private val terminalApproval = PostCreateApproval { repository, commands ->
        if (confirm("${commandLines(repository, commands)}Run these commands? [y/N] ")) PostCreateDecision.RUN else PostCreateDecision.SKIP
    }

    private val contributions = MachineProviderContributions(
        appleSimulators,
        ::simulatorSettingsFor,
        gradleJvmArgsFor = { repository -> effectiveGradleJvmArgs(fileManager, gradleUserHome, repository) },
        fileManager = fileManager,
        userHome = userHome,
        isMac = derivedDataSource.isSupported(),
    )
    private val listDerivedData = ListDerivedDataUseCase(derivedDataSource)
    private val deleteOrphanedDerivedData = DeleteOrphanedDerivedDataUseCase(derivedDataSource)
    private val resolveEnvironment = resolverWith(contributions)
    private val startSimulator = StartWorktreeSimulatorUseCase(provisioningSource, appleSimulators, { repository, worktree, requested ->
        resolverWith(contributions.requesting(requested))(repository, worktree)
    })
    private val stopSimulator = StopWorktreeSimulatorUseCase(provisioningSource, appleSimulators, resolveEnvironment)
    private val readSimulatorStates = ReadSimulatorStatesUseCase(provisioningSource, appleSimulators, resolveEnvironment)
    private val deleteSimulators = DeleteSimulatorsUseCase(provisioningSource, appleSimulators, resolveEnvironment)

    private fun resolverWith(factory: ProviderContributions) =
        ResolveWorktreeEnvironmentUseCase(executor, provisioningSource, environmentStateStore, portProbe, factory)

    private fun simulatorSettingsFor(repositoryPath: String): SimulatorSettings =
        projectRepository.list().firstOrNull { sameDirectory(it.config.baseRepositoryPath, repositoryPath) }
            ?.config?.simulator ?: SimulatorSettings()
    private val prepareEnvironment = PrepareWorktreeEnvironmentUseCase(
        resolveEnvironment,
        WriteEnvironmentFileUseCase(executor, fileManager, provisioningSource),
    )
    private val startEmulator = StartWorktreeEmulatorUseCase(provisioningSource, resolveEnvironment, androidSdk)
    private val stopEmulator = StopWorktreeEmulatorUseCase(provisioningSource, resolveEnvironment, androidSdk)
    private val readEmulatorStates = ReadEmulatorStatesUseCase(provisioningSource, resolveEnvironment, androidSdk)

    fun run(args: List<String>): Int = when (val parsed = parseArguments(args)) {
        is ParsedArguments.UsageError -> {
            err.line("shogun: ${parsed.message}")
            err.line("Run 'shogun help' for usage.")
            EXIT_USAGE
        }
        is ParsedArguments.Command -> runBlocking { cancelledOnShutdown { execute(parsed.command) } }
    }

    private suspend fun <T> cancelledOnShutdown(block: suspend () -> T): T {
        val job = currentCoroutineContext().job
        val hook = Thread {
            job.cancel()
            runBlocking { job.join() }
        }
        Runtime.getRuntime().addShutdownHook(hook)
        try {
            return block()
        } finally {
            runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
        }
    }

    private suspend fun execute(command: CliCommand): Int = try {
        when (command) {
            CliCommand.Help -> out.append(CliHelp.USAGE).let { EXIT_OK }
            is CliCommand.CommandHelp -> out.append(CliHelp.forCommand(command.name).orEmpty()).let { EXIT_OK }
            CliCommand.Version -> out.line("shogun ${BuildInfo.VERSION}").let { EXIT_OK }
            is CliCommand.New -> new(command)
            is CliCommand.Provision -> provision(command)
            is CliCommand.Trust -> trust(command)
            is CliCommand.Remove -> remove(command)
            is CliCommand.Repair -> repair(command)
            is CliCommand.Open -> open(command)
            is CliCommand.Status -> status(command)
            is CliCommand.Clean -> clean(command)
            CliCommand.HookWorktreeCreate -> hookWorktreeCreate()
            CliCommand.HookWorktreeRemove -> hookWorktreeRemove()
            is CliCommand.Gradle -> gradle(command)
            is CliCommand.Env -> env(command)
            is CliCommand.Emulator -> emulator(command)
            is CliCommand.Simulator -> simulator(command)
            is CliCommand.SimulatorGradleInit -> simulatorGradleInit(command)
            CliCommand.HookSessionStart -> hookSessionStart()
            CliCommand.HookHerdr -> hookHerdr()
            is CliCommand.GradleStop -> gradleStop(command)
            is CliCommand.Xcode -> xcode(command)
            is CliCommand.XcodeClean -> xcodeClean(command)
        }
    } catch (error: WorktreeError) {
        err.line("shogun: ${error.message}")
        if (error is WorktreeError.GitCommandFailed) classifyGitFailure(error.errorOutput).hint()?.let { err.line("hint: $it") }
        EXIT_FAILURE
    } catch (error: CommandExecutionException) {
        err.line("shogun: ${error.message}")
        EXIT_FAILURE
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        err.line("shogun: ${error.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: error::class.simpleName}")
        EXIT_FAILURE
    }

    private suspend fun new(command: CliCommand.New): Int {
        val config = configFor(workingDirectory, worktreesRoot = command.worktreesRoot?.let(::absolute))
        if (config.worktreesRoot.isBlank()) {
            err.line("shogun: this repository has no ShogunAi project, so there's no worktrees folder.")
            err.line("Pass --worktrees-root <dir>, or add the repository as a project in the desktop app.")
            return EXIT_USAGE
        }
        if (command.fetch) FetchRemoteUseCase(config, executor)().getOrThrow()
        val approval = if (command.json) PostCreateApproval.SKIP else terminalApproval
        val created = if (command.branch != null) {
            CreateWorktreeFromBranchUseCase(
                config, executor, fileManager, provisioningSource, prepareEnvironment, trust, approval,
            )(command.branch)
        } else {
            val startPoint = resolveStartPoint(workingDirectory, command.from)
            CreateWorktreeUseCase(config, executor, fileManager, provisioningSource, prepareEnvironment, trust, approval)(
                command.taskId.orEmpty(),
                command.branchType,
                startPoint,
            )
        }
        val failure = created.exceptionOrNull()
        if (failure is WorktreeError.PostCreateCommandFailed) {
            if (command.json) {
                printJson(newJson(failure.worktreePath, branchOf(config, failure.worktreePath), emptyList(), failure))
            } else {
                out.line(failure.worktreePath)
            }
            err.line("shogun: ${failure.message}")
            return EXIT_FAILURE
        }
        val worktree = created.getOrThrow()
        if (command.json) {
            printJson(newJson(worktree.path, worktree.branch, worktree.skippedPostCreateCommands, null))
            return EXIT_OK
        }
        out.line(worktree.path)
        noteSkippedCommands(config.baseRepositoryPath, worktree.skippedPostCreateCommands)
        return EXIT_OK
    }

    private suspend fun branchOf(config: ProjectConfig, path: String): String? =
        ListWorktreesUseCase(config, executor)().getOrNull()?.firstOrNull { sameDirectory(it.path, path) }?.branch

    private fun printJson(json: JsonObject) = out.line(json.compact())

    private fun saying(json: Boolean): (String) -> Unit = if (json) { _ -> } else { text -> out.line(text) }

    private fun noteSkippedCommands(repository: String, skipped: List<List<String>>, prefix: String = "shogun") {
        if (skipped.isEmpty()) return
        val count = if (skipped.size == 1) "1 post-creation command" else "${skipped.size} post-creation commands"
        err.line("$prefix: skipped $count not approved on this machine; run 'shogun trust' in $repository to review them")
    }

    private fun commandLines(repository: String, commands: List<List<String>>): String =
        "Post-creation commands declared by $repository:\n" + commands.joinToString("") { "  ${shellLine(it)}\n" }

    private fun shellLine(command: List<String>): String = ShellQuoting.posixCommand(command)

    private fun confirm(question: String): Boolean {
        err.append(question)
        val answer = readAnswer() ?: return false.also { err.line("") }
        return answer.trim().lowercase() in setOf("y", "yes")
    }

    private suspend fun trust(command: CliCommand.Trust): Int {
        val repository = configFor(absolute(command.path ?: workingDirectory)).baseRepositoryPath
        if (command.json) return trustAsJson(command, repository)
        if (command.revoke) {
            trust.revoke(repository)
            out.line("revoked the approval of $repository")
            return EXIT_OK
        }
        val commands = provisioningSource.read(repository).postCreateCommands
        if (commands.isEmpty()) {
            out.line("$repository declares no post-creation commands.")
            return EXIT_OK
        }
        val approved = trust.isApproved(repository, commands)
        out.line("Post-creation commands declared by $repository:")
        commands.forEach { out.line("  ${shellLine(it)}") }
        out.line(if (approved) "approved on this machine" else "not approved on this machine")
        if (approved) return EXIT_OK
        if (command.yes || confirm("Approve these commands? [y/N] ")) {
            trust.approve(repository, commands)
            out.line("approved")
        }
        return EXIT_OK
    }

    private fun trustAsJson(command: CliCommand.Trust, repository: String): Int {
        val declared = runCatching { provisioningSource.read(repository).postCreateCommands }
        val commands = if (command.revoke) declared.getOrDefault(emptyList()) else declared.getOrThrow()
        val wasApproved = commands.isEmpty() || trust.isApproved(repository, commands)
        val changed = when {
            command.revoke -> (commands.isNotEmpty() && wasApproved).also { trust.revoke(repository) }
            command.yes && !wasApproved -> true.also { trust.approve(repository, commands) }
            else -> false
        }
        val approved = if (command.revoke) commands.isEmpty() else wasApproved || changed
        printJson(trustJson(repository, commands, approved, changed))
        return EXIT_OK
    }

    private suspend fun resolveStartPoint(directory: String, from: String?): String? {
        val resolve = ResolveStartPointUseCase(executor)
        return if (from != null) {
            resolve(directory, from).getOrThrow()
        } else {
            resolve(directory, "HEAD").getOrElse { throw WorktreeError.RepositoryHasNoCommits }
        }
    }

    private suspend fun provision(command: CliCommand.Provision): Int {
        val target = absolute(command.path ?: workingDirectory)
        return when {
            command.check -> checkProvisionedFiles(target, command.json)
            command.json -> provisionWorktreeAt(target, PostCreateApproval.SKIP, json = true)
            else -> provisionWorktreeAt(target, terminalApproval)
        }
    }

    private suspend fun checkProvisionedFiles(target: String, json: Boolean): Int {
        val config = configFor(target)
        val worktree = ListWorktreesUseCase(config, executor)().getOrThrow()
            .firstOrNull { sameDirectory(it.path, target) }
            ?: throw WorktreeError.NotAWorktree(target)
        val drift = CheckProvisionedFilesUseCase(config, executor, fileManager, provisioningSource)(worktree.path).getOrThrow()
        if (json) printJson(provisionCheckJson(worktree.path, drift)) else drift.forEach { out.line("${it.path}: ${driftLabel(it.state)}") }
        return if (drift.isEmpty()) EXIT_OK else EXIT_DRIFT
    }

    private fun driftLabel(state: ProvisionDriftState): String = when (state) {
        ProvisionDriftState.MISSING -> "missing"
        ProvisionDriftState.DIFFERS -> "differs"
        ProvisionDriftState.NOT_LINKED -> "not linked"
    }

    private fun provisionedLabel(file: ProvisionedFile): String = when (file.mode) {
        ProvisionMode.COPIED -> file.path
        ProvisionMode.LINKED -> "${file.path} (linked)"
        ProvisionMode.COPIED_INSTEAD_OF_LINKING -> "${file.path} (copied: symbolic links aren't available)"
    }

    private suspend fun provisionWorktreeAt(
        target: String,
        approval: PostCreateApproval,
        onProvisioned: suspend (Worktree, ProjectConfig) -> Unit = { _, _ -> },
        json: Boolean = false,
    ): Int {
        val config = configFor(target)
        val worktree = ListWorktreesUseCase(config, executor)().getOrThrow()
            .firstOrNull { sameDirectory(it.path, target) }
            ?: throw WorktreeError.NotAWorktree(target)
        val provisioned = ProvisionWorktreeUseCase(config, executor, fileManager, provisioningSource, prepareEnvironment, trust, approval)(worktree.path)
        val failure = provisioned.exceptionOrNull()
        if (json && failure is WorktreeError.PostCreateCommandFailed) {
            printJson(provisionJson(worktree.path, emptyList(), emptyList(), failure))
            err.line("shogun: ${failure.message}")
            return EXIT_FAILURE
        }
        val result = provisioned.getOrThrow()
        if (json) {
            printJson(provisionJson(worktree.path, result.files, result.skippedPostCreateCommands, null))
            return EXIT_OK
        }
        result.files.forEach { out.line(provisionedLabel(it)) }
        noteSkippedCommands(config.baseRepositoryPath, result.skippedPostCreateCommands)
        onProvisioned(worktree, config)
        return EXIT_OK
    }

    private suspend fun status(command: CliCommand.Status): Int {
        val config = configFor(workingDirectory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val mainBranch = ResolveMainBranchUseCase(config, executor)().getOrNull()
        val readStatus = ReadWorktreeStatusUseCase(executor)
        val herdrWorktreesRoot = herdrConfig.worktreesRoot()
        val agents = DetectRunningAgentsUseCase(executor, herdr)(config, worktrees.filterNot { it.isPrunable }).getOrNull().orEmpty()
        val entries = worktrees.map { worktree ->
            val read = if (worktree.isPrunable) null else readStatus(worktree, mainBranch)
            WorktreeEntry(
                worktree,
                read?.getOrNull(),
                classifyOrigin(worktree, config, herdrWorktreesRoot),
                read?.exceptionOrNull()?.let(::failureReason),
                agents[worktree.path].orEmpty(),
            )
        }
        if (command.json) {
            printJson(statusJson(config.baseRepositoryPath, mainBranch?.name, entries))
        } else {
            entries.forEach { out.line(statusLine(it)) }
        }
        return EXIT_OK
    }

    private val releaseResources = ReleaseWorktreeResourcesUseCase(
        provisioningSource,
        readEmulatorStates,
        stopEmulator,
        readSimulatorStates,
        stopSimulator,
        ListGradleDaemonsUseCase(daemonSource, fileManager),
        StopGradleDaemonsUseCase(daemonSource, executor),
    )

    private suspend fun remove(command: CliCommand.Remove): Int {
        val config = configFor(workingDirectory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val worktree = resolveWorktreeTarget(command.target, worktrees, config, ::canonical)
        if (!command.force && !worktree.isPrunable) {
            val agents = DetectRunningAgentsUseCase(executor)(config, listOf(worktree))
                .onFailure { err.line("shogun: couldn't check for running agents, so none were taken into account: ${it.message}") }
                .getOrNull()?.get(worktree.path).orEmpty()
            if (agents.isNotEmpty()) {
                err.line("shogun: ${agents.joinToString { it.name }} is running in ${worktree.path}; use --force to remove it anyway")
                return EXIT_FAILURE
            }
        }
        val plan = releaseResources.plan(config, worktree, worktrees)
        val removal = RemoveWorktreeUseCase(config, executor, CloseHerdrWorkspacesUseCase(config, herdr))(
            worktree.path,
            branchToDelete = worktree.branch?.takeIf { command.deleteBranch || command.forceDeleteBranch },
            force = command.force,
            forceBranchDeletion = command.forceDeleteBranch,
        )
            .getOrElse { failure ->
                if (failure is WorktreeError.GitCommandFailed && classifyGitFailure(failure.errorOutput) == GitFailureCause.LocalChanges) {
                    err.line("shogun: ${worktree.path} has uncommitted or untracked changes; use --force to remove it anyway")
                    return EXIT_FAILURE
                }
                throw failure
            }
        val say = saying(command.json)
        say("removed ${worktree.path}")
        removal.herdrFailure?.let { err.line(herdrCloseFailure(worktree.path, it)) }
        val released = releaseResources.release(config, plan)
        released.stoppedEmulators.forEach { say("stopped emulator $it") }
        released.stoppedSimulators.forEach { say("stopped simulator $it") }
        released.stoppedDaemons.forEach { say("stopped Gradle daemon $it") }
        released.failures.forEach { err.line("shogun: $it") }
        val branch = worktree.branch
        val branchDeleted = branch?.takeIf { removal.branch == BranchDeletion.Deleted }
        val branchKeptReason = branch?.let { removal.branch?.keptReason() }
        if (branch != null && removal.branch != null) {
            say(if (branchDeleted != null) "deleted branch $branch" else "kept branch $branch ($branchKeptReason)")
        }
        if (command.json) {
            printJson(
                removeJson(
                    worktree.path,
                    worktree.branch,
                    branchDeleted,
                    branchKeptReason,
                    released.stoppedEmulators,
                    released.stoppedSimulators,
                    released.stoppedDaemons,
                    released.failures,
                ),
            )
        }
        return if (released.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
    }

    private suspend fun open(command: CliCommand.Open): Int {
        val config = configFor(workingDirectory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val worktree = findWorktreeTarget(command.target, worktrees, config, ::canonical)
        val agent = if (command.agent == null) {
            config.agentLaunchConfig
        } else {
            config.agents.firstOrNull { it.displayName.equals(command.agent, ignoreCase = true) } ?: run {
                err.line("shogun: no agent named '${command.agent}'; this project's agents are ${config.agents.joinToString { it.displayName }}")
                return EXIT_USAGE
            }
        }
        val opening = OpenWorktreeInHerdrUseCase(config, herdr, resolveEnvironment)(worktree, agent).getOrThrow()
        val startedAgent = agent.displayName.takeIf { opening == HerdrOpening.OPENED }
        when {
            command.json -> printJson(openJson(worktree.path, worktree.branch, opening, startedAgent))
            startedAgent != null -> out.line("opened ${worktree.path} in Herdr with $startedAgent")
            else -> out.line("focused ${worktree.path} in Herdr (already open)")
        }
        return EXIT_OK
    }

    private suspend fun repair(command: CliCommand.Repair): Int {
        val projectConfig = configFor(workingDirectory)
        if (projectConfig.worktreesRoot.isBlank()) {
            err.line("shogun: this repository has no ShogunAi project, so there's no worktrees folder to repair into.")
            err.line("Add the repository as a project in the desktop app first.")
            return EXIT_USAGE
        }
        val relinked = RepairWorktreesUseCase(projectConfig, executor, fileManager)().getOrThrow()
        when {
            command.json -> printJson(repairJson(relinked))
            relinked.isEmpty() -> out.line("Nothing to repair.")
            else -> relinked.forEach { out.line("repaired $it") }
        }
        return EXIT_OK
    }

    private fun canonical(path: String): String = runCatching { canonicalPath(absolute(path)) }.getOrDefault(path)

    private suspend fun clean(command: CliCommand.Clean): Int {
        val say = saying(command.json)
        val config = configFor(workingDirectory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val mainBranch = ResolveMainBranchUseCase(config, executor)().getOrNull()
        val readStatus = ReadWorktreeStatusUseCase(executor)
        val statusReads = worktrees
            .filterNot { it.isMain || it.isPrunable || it.isLocked }
            .associateWith { worktree -> readStatus(worktree, mainBranch) }
        val statuses = statusReads.mapNotNull { (worktree, read) -> read.getOrNull()?.let { worktree.path to it } }.toMap()
        val agents = DetectRunningAgentsUseCase(executor, herdr)(config, worktrees.filterNot { it.isMain || it.isPrunable })
            .onFailure { err.line("shogun: couldn't check for running agents, so none were taken into account: ${it.message}") }
            .getOrNull()
        val selection = selectCleanup(worktrees, statuses, agents)
        val candidates = selection.candidates
        selection.keptForRunningAgent.forEach { (worktree, running) ->
            say("kept ${worktree.path} (${running.joinToString { it.name }} is running)")
        }
        val skipped = statusReads.mapNotNull { (worktree, read) ->
            read.exceptionOrNull()?.let { CleanSkippedJson(worktree.path, failureReason(it)) }
        }
        skipped.forEach { say("skipped ${it.path} (status unavailable: ${it.reason})") }
        val plan = CleanJson(
            dryRun = !command.confirmed,
            candidates = candidates.map { CleanCandidateJson(it.path, it.branch, it.isPrunable) },
            keptForAgents = selection.keptForRunningAgent.map { (worktree, running) ->
                CleanKeptJson(worktree.path, running.map { it.name })
            },
            skipped = skipped,
        )
        val simulators = if (command.deleteSimulators) simulatorsBeforeCleanup(config, candidates) else SimulatorCleanup()
        val derivedDataOrphans = if (command.deleteDerivedData && derivedDataSource.isSupported() && xcodeProvider(config.baseRepositoryPath) != null) {
            listDerivedData(worktrees, includeCompilationCache = false, measure = DerivedDataMeasure.ORPHANS)
                .onFailure { err.line("shogun: couldn't list DerivedData: ${it.message}") }
                .getOrNull()?.orphans.orEmpty()
        } else {
            emptyList()
        }
        if (candidates.isEmpty() && simulators.orphans.isEmpty() && derivedDataOrphans.isEmpty()) {
            if (command.json) printJson(cleanJson(plan)) else out.line("Nothing to clean up.")
            return EXIT_OK
        }
        val daemons = if (command.stopGradleDaemons) daemonsBeforeCleanup(worktrees) else emptyList()
        val emulators = if (command.stopEmulators) emulatorsBeforeCleanup(config, candidates) else emptyMap()
        val closeHerdrWorkspaces = CloseHerdrWorkspacesUseCase(config, herdr)
        if (!command.confirmed) {
            if (command.json) {
                printJson(cleanJson(plan))
                return EXIT_OK
            }
            val herdrWorkspaces = closeHerdrWorkspaces.openWorkspaces(candidates.map { it.path })
            candidates.forEach {
                out.line("would remove ${it.path}${if (it.isPrunable) " (folder missing)" else ""}")
                if (it.path in herdrWorkspaces) out.line("would close Herdr workspace of ${it.path}")
            }
            idleDaemonsOfRemovedWorktrees(daemons, worktrees, candidates.map { it.path })
                .forEach { out.line("would stop Gradle daemon $it") }
            emulators.values.forEach { out.line("would stop emulator $it") }
            simulators.byWorktree.values.forEach { out.line("would delete simulator ${it.name} (${it.udid})") }
            simulators.orphans.forEach { out.line("would delete orphaned simulator ${it.name} (${it.udid})") }
            derivedDataOrphans.forEach { out.line("would delete orphaned DerivedData ${it.path}") }
            out.line(dryRunHint(command, candidates.size, simulators.orphans.isNotEmpty(), derivedDataOrphans.isNotEmpty()))
            return EXIT_OK
        }
        val report = CleanUpWorktreesUseCase(config, executor, closeHerdrWorkspaces)(candidates, mainBranch, command.deleteBranches).getOrThrow()
        report.removed.forEach { say("removed $it") }
        report.keptBranches.forEach { say("kept branch $it (not merged)") }
        report.failures.forEach { err.line("shogun: couldn't remove ${it.path}: ${it.error.message}") }
        report.herdrFailures.forEach { err.line(herdrCloseFailure(it.path, it.error)) }
        val deletedBranches = if (command.deleteBranches) {
            candidates.filter { it.path in report.removed }.mapNotNull { it.branch }.filterNot { it in report.keptBranches }
        } else {
            emptyList()
        }
        val pids = idleDaemonsOfRemovedWorktrees(daemons, worktrees, report.removed)
        val stoppedDaemons = if (pids.isNotEmpty()) stopDaemonsAfterCleanup(pids, say) else emptyList()
        val removedEmulators = report.removed.mapNotNull { emulators[it] }
        val stoppedEmulators = if (removedEmulators.isNotEmpty()) stopEmulatorsAfterCleanup(config, removedEmulators, say) else emptyList()
        var deletedSimulators = emptyList<String>()
        if (command.deleteSimulators) {
            val orphansNow = deleteSimulators.orphans(config).getOrDefault(simulators.orphans)
            val removedSimulators = report.removed.mapNotNull { simulators.byWorktree[it]?.udid } + orphansNow.map { it.udid }
            if (removedSimulators.isNotEmpty()) deletedSimulators = deleteSimulatorsAfterCleanup(removedSimulators.distinct(), say)
        }
        var derivedDataFailed = false
        val derivedDataDeleted = mutableListOf<String>()
        val derivedDataFailures = mutableListOf<CleanFailureJson>()
        if (command.deleteDerivedData && derivedDataSource.isSupported() && xcodeProvider(config.baseRepositoryPath) != null) {
            deleteOrphanedDerivedData()
                .onSuccess { deletion ->
                    deletion.deleted.forEach {
                        derivedDataDeleted += it.path
                        say("deleted DerivedData ${it.path}")
                    }
                    deletion.failures.forEach {
                        derivedDataFailed = true
                        derivedDataFailures += CleanFailureJson(it.path, it.detail)
                        err.line("shogun: couldn't delete DerivedData ${it.path}: ${it.detail}")
                    }
                }
                .onFailure {
                    derivedDataFailed = true
                    err.line("shogun: couldn't delete DerivedData: ${it.message}")
                }
        }
        if (command.json) {
            printJson(
                cleanJson(
                    plan.copy(
                        removed = report.removed,
                        deletedBranches = deletedBranches,
                        failures = report.failures.map { CleanFailureJson(it.path, it.error.message.orEmpty()) } + derivedDataFailures,
                        gradleDaemonsStopped = stoppedDaemons,
                        emulatorsStopped = stoppedEmulators,
                        simulatorsDeleted = deletedSimulators,
                        derivedDataDeleted = derivedDataDeleted,
                    ),
                ),
            )
        }
        return if (report.failures.isEmpty() && !derivedDataFailed) EXIT_OK else EXIT_FAILURE
    }

    private fun dryRunHint(command: CliCommand.Clean, worktrees: Int, simulatorOrphans: Boolean, derivedDataOrphans: Boolean): String {
        val flags = listOfNotNull(
            "--delete-branches".takeIf { command.deleteBranches },
            "--stop-gradle-daemons".takeIf { command.stopGradleDaemons },
            "--stop-emulators".takeIf { command.stopEmulators },
            "--delete-simulators".takeIf { command.deleteSimulators },
            "--delete-derived-data".takeIf { command.deleteDerivedData },
            "--yes",
        )
        val what = listOfNotNull(
            when (worktrees) {
                0 -> null
                1 -> "the worktree"
                else -> "the worktrees"
            },
            "the orphaned simulators".takeIf { simulatorOrphans },
            "the orphaned DerivedData".takeIf { derivedDataOrphans },
        )
        val listed = if (what.size > 1) what.dropLast(1).joinToString(", ") + " and " + what.last() else what.single()
        return "Run 'shogun clean ${flags.joinToString(" ")}' to remove $listed."
    }

    private fun failureReason(error: Throwable): String {
        val text = (error as? WorktreeError.GitCommandFailed)?.errorOutput?.takeIf { it.isNotBlank() } ?: error.message.orEmpty()
        return text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: error::class.simpleName.orEmpty()
    }

    private data class SimulatorCleanup(
        val byWorktree: Map<String, WorktreeSimulator> = emptyMap(),
        val orphans: List<SimulatorDevice> = emptyList(),
    )

    private suspend fun simulatorsBeforeCleanup(config: ProjectConfig, candidates: List<Worktree>): SimulatorCleanup =
        SimulatorCleanup(
            byWorktree = readSimulatorStates(config, candidates)
                .onFailure { err.line("shogun: couldn't read simulators: ${it.message}") }
                .getOrDefault(emptyMap()),
            orphans = deleteSimulators.orphans(config)
                .onFailure { err.line("shogun: couldn't look for orphaned simulators: ${it.message}") }
                .getOrDefault(emptyList()),
        )

    private suspend fun deleteSimulatorsAfterCleanup(udids: List<String>, say: (String) -> Unit): List<String> =
        deleteSimulators(udids)
            .onSuccess { deleted -> deleted.forEach { say("deleted simulator $it") } }
            .onFailure { err.line("shogun: couldn't delete simulators: ${it.message}") }
            .getOrDefault(emptyList())

    private suspend fun simulator(command: CliCommand.Simulator): Int {
        val (config, worktree) = configAndWorktreeContaining(absolute(command.path ?: workingDirectory))
        val simulator = when (command.action) {
            SimulatorAction.START ->
                startSimulator(config, worktree.path, SimulatorSettings(command.device, command.runtime)).getOrThrow()
            SimulatorAction.STOP -> stopSimulator(config, worktree.path).getOrThrow()
            SimulatorAction.STATUS -> {
                if (provisioningSource.read(config.baseRepositoryPath).providers.iosSimulator == null) {
                    throw WorktreeError.IosSimulatorProviderNotDeclared
                }
                if (!appleSimulators.isSupported()) throw WorktreeError.IosSimulatorsNotSupported
                readSimulatorStates(config, listOf(worktree)).getOrThrow()[worktree.path]
            }
        }
        if (command.json) {
            printJson(simulatorJson(worktree.path, simulator))
            return EXIT_OK
        }
        if (simulator == null) {
            out.line("No simulator for this worktree yet; 'shogun simulator start' creates and boots one.")
        } else {
            out.line("${simulator.udid} ${simulator.state.name.lowercase()}  ${simulator.name}")
        }
        if (command.action == SimulatorAction.STATUS) printKmpWiring(config)
        return EXIT_OK
    }

    private fun printKmpWiring(config: ProjectConfig) {
        if (!kmpTestWiring.usesKotlinMultiplatform(config.baseRepositoryPath)) return
        if (kmpTestWiring.isWiredInRepository(config.baseRepositoryPath) || kmpTestWiring.isInitScriptInstalled()) {
            out.line("KMP simulator tests use this worktree's simulator.")
            return
        }
        out.line("KMP simulator tests don't use this worktree's simulator yet. Add this to the module's build.gradle.kts:")
        out.line(FileKmpTestWiring.REPOSITORY_SNIPPET)
        out.line("or run 'shogun simulator gradle-init install' to do it for every project on this machine.")
    }

    private fun simulatorGradleInit(command: CliCommand.SimulatorGradleInit): Int {
        val path = kmpTestWiring.initScriptPath()
        if (!command.install) {
            kmpTestWiring.uninstallInitScript()
            out.line("Removed $path")
            return EXIT_OK
        }
        out.line("This writes $path, which makes the iOS simulator tests of every Gradle project on this machine")
        out.line("run on the simulator in IOS_SIMULATOR_UDID when that variable is set:")
        out.line(FileKmpTestWiring.INIT_SCRIPT.trimEnd())
        if (!command.confirmed) {
            out.append("Install it? [y/N] ")
            val answer = readInput().trim().lowercase()
            if (answer != "y" && answer != "yes") {
                out.line("Nothing installed.")
                return EXIT_OK
            }
        }
        kmpTestWiring.installInitScript()
        out.line("Installed $path")
        return EXIT_OK
    }

    private suspend fun emulatorsBeforeCleanup(config: ProjectConfig, candidates: List<Worktree>): Map<String, String> =
        readEmulatorStates(config, candidates)
            .onFailure { err.line("shogun: couldn't read emulators: ${it.message}") }
            .getOrDefault(emptyMap())
            .filterValues { it.state != EmulatorState.STOPPED }
            .mapValues { it.value.serial }

    private suspend fun stopEmulatorsAfterCleanup(config: ProjectConfig, serials: List<String>, say: (String) -> Unit): List<String> =
        runCatching { stopEmulator.stopSerials(config, serials) }
            .onSuccess { stopped -> stopped.forEach { say("stopped emulator ${it.serial}") } }
            .onFailure { err.line("shogun: couldn't stop emulators: ${it.message}") }
            .getOrDefault(emptyList())
            .map { it.serial }

    private suspend fun emulator(command: CliCommand.Emulator): Int {
        val target = absolute(command.path ?: workingDirectory)
        val (config, worktree) = configAndWorktreeContaining(target)
        val emulator = when (command.action) {
            EmulatorAction.START ->
                startEmulator(config, worktree.path, command.avd, command.noWindow.takeIf { it }).getOrThrow()
            EmulatorAction.STOP -> stopEmulator(config, worktree.path).getOrThrow()
            EmulatorAction.STATUS -> {
                if (provisioningSource.read(config.baseRepositoryPath).providers.emulator == null) {
                    throw WorktreeError.EmulatorProviderNotDeclared
                }
                readEmulatorStates(config, listOf(worktree)).getOrThrow()[worktree.path]
            }
        }
        if (command.json) {
            printJson(emulatorJson(worktree.path, emulator))
            return EXIT_OK
        }
        if (emulator == null) {
            out.line("No emulator port reserved for this worktree yet; 'shogun emulator start' reserves one and starts it.")
        } else {
            out.line("${emulator.serial} ${emulator.state.name.lowercase()}")
        }
        return EXIT_OK
    }

    private suspend fun daemonsBeforeCleanup(worktrees: List<Worktree>): List<GradleDaemon> =
        ListGradleDaemonsUseCase(daemonSource, fileManager)(worktrees)
            .onFailure { err.line("shogun: couldn't list Gradle daemons: ${it.message}") }
            .getOrDefault(emptyList())

    private suspend fun stopDaemonsAfterCleanup(pids: List<Long>, say: (String) -> Unit): List<Long> =
        StopGradleDaemonsUseCase(daemonSource, executor)(pids)
            .onSuccess { report ->
                printStopReport(report, say)
                report.failures.forEach { err.line("shogun: couldn't stop Gradle daemon ${it.pid}: ${it.detail}") }
            }
            .onFailure { err.line("shogun: couldn't stop Gradle daemons: ${it.message}") }
            .getOrNull()?.stopped?.map { it.pid }.orEmpty()

    private suspend fun env(command: CliCommand.Env): Int {
        val target = absolute(command.path ?: workingDirectory)
        val (repository, worktree) = worktreeContaining(target)
        val environment = resolveEnvironment(repository, worktree.path).getOrThrow()
        out.append(formatEnvironment(environment, command.format).takeIf { !environment.isEmpty }.orEmpty())
        return EXIT_OK
    }

    private suspend fun hookSessionStart(): Int {
        val input = hookInput("session-start") ?: return EXIT_USAGE
        val directory = input.string("cwd") ?: workingDirectory
        if (ResolveRepositoryUseCase(executor)(directory).isFailure) return EXIT_OK
        val (repository, worktree) = worktreeContaining(directory)
        val environment = resolveEnvironment(repository, worktree.path).getOrThrow()
        if (environment.isEmpty) return EXIT_OK
        environmentVariable(CLAUDE_ENV_FILE)?.takeIf { it.isNotBlank() }?.let { path ->
            appendToEnvironmentFile(path, formatEnvironment(environment, EnvironmentFormat.SH))
        }
        val variables = environment.variables.joinToString(", ") { (name, value) -> "$name=$value" }
        val emulatorHint = environment.variables.toMap()[ANDROID_SERIAL]
            ?.takeIf { provisioningSource.read(repository).providers.emulator != null }
            ?.let { serial ->
                ". Its Android emulator is $ANDROID_SERIAL=$serial; start it with 'shogun emulator start' before installing the app or running instrumented tests. " +
                    "The adb server is shared with other worktrees' agents: never run 'adb kill-server' or 'adb start-server', and don't pass -s, -e or -d to adb, so every command goes to $ANDROID_SERIAL"
            }
            .orEmpty()
        val simulatorHint = environment.variables.toMap()[IOS_SIMULATOR_UDID]
            ?.takeIf { provisioningSource.read(repository).providers.iosSimulator != null }
            ?.let { udid ->
                ". Its iOS simulator is $IOS_SIMULATOR_UDID=$udid; boot it with 'shogun simulator start' and build with " +
                    "xcodebuild -destination \"id=\$$IOS_SIMULATOR_UDID\""
            }
            .orEmpty()
        out.line("ShogunAi environment of worktree ${folderName(worktree.path)}: $variables$emulatorHint$simulatorHint")
        return EXIT_OK
    }

    private suspend fun worktreeContaining(directory: String): Pair<String, Worktree> {
        val (config, worktree) = configAndWorktreeContaining(directory)
        return config.baseRepositoryPath to worktree
    }

    private suspend fun configAndWorktreeContaining(directory: String): Pair<ProjectConfig, Worktree> {
        val config = configFor(directory)
        val canonicalDirectory = runCatching { canonicalPath(directory) }.getOrDefault(directory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val canonical = worktrees.associateBy { runCatching { canonicalPath(it.path) }.getOrDefault(it.path) }
        val owner = owningWorktree(canonicalDirectory, canonical.map { (path, worktree) -> worktree.copy(path = path) })
            ?: throw WorktreeError.NotAWorktree(directory)
        return config to canonical.getValue(owner.path)
    }

    private suspend fun gradle(command: CliCommand.Gradle): Int {
        val repository = ResolveRepositoryUseCase(executor)(workingDirectory).getOrNull()
        val worktrees = repository?.let { ListWorktreesUseCase(configFor(workingDirectory), executor)().getOrNull() }
        val daemons = ListGradleDaemonsUseCase(daemonSource, fileManager)(worktrees).getOrThrow()
        if (command.json) {
            printJson(gradleJson(daemons, attributed = worktrees != null))
            return EXIT_OK
        }
        if (daemons.isEmpty()) {
            out.line("No Gradle or Kotlin daemons running.")
        } else {
            daemons.forEach { out.line(daemonLine(it, attributed = worktrees != null)) }
            out.line("total  memory=${formatMemory(totalResidentMemory(daemons))}  daemons=${daemons.size}")
        }
        if (repository != null && declaresGradle(repository) &&
            !CheckGradleBuildCacheUseCase(fileManager, gradleUserHome)(repository)
        ) {
            out.line(BUILD_CACHE_NOTICE)
        }
        return EXIT_OK
    }

    private suspend fun gradleStop(command: CliCommand.GradleStop): Int {
        val report = StopGradleDaemonsUseCase(daemonSource, executor)(command.pids.ifEmpty { null }).getOrThrow()
        if (command.json) {
            printJson(gradleStopJson(report))
            report.notDaemons.forEach { err.line("shogun: $it isn't a Gradle or Kotlin daemon") }
            report.failures.forEach { err.line("shogun: couldn't stop ${it.pid}: ${it.detail}") }
            return if (report.notDaemons.isEmpty() && report.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
        }
        if (command.pids.isEmpty() && report.stopped.isEmpty() && report.kept.isEmpty() && report.failures.isEmpty()) {
            out.line("No Gradle or Kotlin daemons running.")
        }
        printStopReport(report, saying(false))
        report.notDaemons.forEach { err.line("shogun: $it isn't a Gradle or Kotlin daemon") }
        report.failures.forEach { err.line("shogun: couldn't stop ${it.pid}: ${it.detail}") }
        return if (report.notDaemons.isEmpty() && report.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
    }

    private fun printStopReport(report: DaemonStopReport, say: (String) -> Unit) {
        report.stopped.forEach { say("stopped ${it.pid}  ${kindLabel(it)}") }
        report.kept.forEach { say("kept ${it.pid}  ${it.kind.label}  (${it.reason.label})") }
    }

    private suspend fun xcode(command: CliCommand.Xcode): Int {
        val repository = ResolveRepositoryUseCase(executor)(workingDirectory).getOrNull()
        val provider = repository?.let { xcodeProvider(it) }
        if (repository == null || provider == null || !derivedDataSource.isSupported()) {
            if (command.json) printJson(xcodeJson(active = false, listing = null)) else out.line("The Xcode provider isn't active.")
            return EXIT_OK
        }
        val config = configFor(workingDirectory)
        val worktrees = ListWorktreesUseCase(config, executor)().getOrThrow()
        val listing = listDerivedData(worktrees, includeCompilationCache = provider.compilationCache, measure = DerivedDataMeasure.ALL).getOrThrow()
        if (command.json) {
            printJson(xcodeJson(active = true, listing = listing))
            return EXIT_OK
        }
        listing.ofWorktrees().forEach { item ->
            val worktree = (item.owner as DerivedDataOwner.Worktree).path
            out.line("$worktree  ${formatMemory(item.folder.sizeBytes ?: 0L)}")
        }
        val orphans = listing.orphans
        out.line("orphans: ${orphans.size} (${formatMemory(orphans.sumOf { it.sizeBytes ?: 0L })})")
        val unavailableCount = listing.folders.count { it.owner == DerivedDataOwner.UnavailableVolume }
        if (unavailableCount > 0) {
            out.line("folders on unavailable volumes: $unavailableCount")
        }
        if (provider.compilationCache) {
            out.line("compilation cache: ${formatMemory(listing.compilationCacheBytes ?: 0L)}")
        }
        return EXIT_OK
    }

    private suspend fun xcodeClean(command: CliCommand.XcodeClean): Int {
        val repository = ResolveRepositoryUseCase(executor)(workingDirectory).getOrNull()
        val provider = repository?.let { xcodeProvider(it) }
        if (repository == null || provider == null || !derivedDataSource.isSupported()) {
            if (command.json) printJson(xcodeCleanJson(active = false, deletion = null)) else out.line("The Xcode provider isn't active.")
            return EXIT_OK
        }
        val deletion = deleteOrphanedDerivedData().getOrThrow()
        if (command.json) {
            printJson(xcodeCleanJson(active = true, deletion = deletion))
            deletion.failures.forEach { err.line("shogun: couldn't delete ${it.path}: ${it.detail}") }
            return if (deletion.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
        }
        if (deletion.deleted.isEmpty() && deletion.failures.isEmpty()) {
            out.line("Nothing to delete.")
            return EXIT_OK
        }
        deletion.deleted.forEach { out.line("deleted ${it.path}") }
        if (deletion.deleted.isNotEmpty()) {
            out.line("freed ${formatMemory(deletion.bytesFreed)}")
        }
        deletion.failures.forEach { err.line("shogun: couldn't delete ${it.path}: ${it.detail}") }
        return if (deletion.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
    }

    private fun xcodeProvider(repository: String): XcodeProvider? =
        runCatching { provisioningSource.read(repository).providers.xcode }.getOrNull()

    private fun declaresGradle(repository: String): Boolean =
        runCatching { provisioningSource.read(repository).providers.gradle != null }.getOrDefault(false)

    private fun daemonLine(daemon: GradleDaemon, attributed: Boolean): String {
        val process = daemon.process
        val details = buildList {
            add(kindLabel(process))
            add("memory=${formatMemory(process.residentMemoryBytes)}")
            when (val state = process.state) {
                DaemonState.Busy -> add("state=busy")
                is DaemonState.Idle -> add("state=idle  idle-for=${formatDuration(Duration.between(state.since, now()))}")
                DaemonState.Unknown -> add("state=unknown")
                DaemonState.Untracked -> Unit
            }
            if (attributed) {
                when (val owner = daemon.owner) {
                    is DaemonOwner.Worktree -> add("worktree=${owner.path}")
                    DaemonOwner.RemovedWorktree -> add("worktree=removed")
                    DaemonOwner.OtherProject -> add("worktree=other-project")
                    null -> Unit
                }
            }
        }
        return (listOf(process.pid.toString()) + details).joinToString("  ")
    }

    private fun kindLabel(process: DaemonProcess): String =
        listOfNotNull(process.kind.label, process.gradleVersion).joinToString(" ")

    private suspend fun hookWorktreeCreate(): Int {
        val input = hookInput("worktree-create") ?: return EXIT_USAGE
        val name = input.string("name") ?: return hookUsageError("worktree-create", "the input has no \"name\"")
        val directory = input.string("cwd") ?: workingDirectory
        val projectConfig = configFor(directory)
        val config = if (projectConfig.worktreesRoot.isBlank()) {
            projectConfig.copy(worktreesRoot = "${projectConfig.baseRepositoryPath.trimEnd('/')}/$CLAUDE_CODE_WORKTREES")
        } else {
            projectConfig
        }
        val target = config.worktreePathFor(normalizeTaskId(name))
        val existing = ListWorktreesUseCase(config, executor)().getOrThrow()
            .firstOrNull { !it.isMain && sameDirectory(it.path, target) }
        val path = existing?.path ?: run {
            val created = CreateWorktreeUseCase(config, executor, fileManager, provisioningSource, prepareEnvironment, trust)(
                name,
                BranchType.FEATURE,
                resolveStartPoint(directory, null),
            )
            when (val failure = created.exceptionOrNull()) {
                null -> created.getOrThrow().also {
                    noteSkippedCommands(config.baseRepositoryPath, it.skippedPostCreateCommands, "shogun: hook worktree-create")
                }.path
                is WorktreeError.PostCreateCommandFailed -> {
                    err.line("shogun: hook worktree-create: ${failure.message}")
                    failure.worktreePath
                }
                else -> throw failure
            }
        }
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
        val removal = RemoveWorktreeUseCase(config, executor, CloseHerdrWorkspacesUseCase(config, herdr))(
            worktree.path,
            branchToDelete = worktree.branch,
            force = true,
        ).getOrThrow()
        removal.herdrFailure?.let { err.line(herdrCloseFailure(worktree.path, it)) }
        reportKeptBranch(worktree.branch, removal.branch)
        return EXIT_OK
    }

    private fun herdrCloseFailure(path: String, error: WorktreeError) =
        "shogun: couldn't close the Herdr workspace of $path: ${error.herdrDetail()}"

    private suspend fun hookHerdr(): Int {
        val event = environmentVariable(HERDR_PLUGIN_EVENT)?.takeIf { it.isNotBlank() }
            ?: return hookUsageError("herdr", "$HERDR_PLUGIN_EVENT isn't set; run it from Herdr's plugin")
        if (event != HERDR_WORKTREE_CREATED && event != HERDR_WORKTREE_REMOVED) return EXIT_OK
        val json = environmentVariable(HERDR_PLUGIN_EVENT_JSON)
            ?: return hookUsageError("herdr", "$HERDR_PLUGIN_EVENT_JSON isn't set")
        val envelope = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject
            ?: return hookUsageError("herdr", "$HERDR_PLUGIN_EVENT_JSON isn't JSON")
        val data = envelope["data"] as? JsonObject
        val removed = data?.get("worktree") as? JsonObject
        val path = removed?.string("path")
            ?: return hookUsageError("herdr", "the event has no \"data.worktree.path\"")
        if (event == HERDR_WORKTREE_CREATED) return provisionWorktreeAt(path, PostCreateApproval.SKIP, ::flagOutsideWorktreesRoot)
        val repoRoot = ((data.get("workspace") as? JsonObject)?.get("worktree") as? JsonObject)?.string("repo_root")
        return deleteMergedBranchOfRemovedWorktree(path, removed.string("branch"), repoRoot)
    }

    private suspend fun deleteMergedBranchOfRemovedWorktree(path: String, branch: String?, repoRoot: String?): Int {
        val projects = projectRepository.list()
        val project = if (repoRoot != null) {
            projects.firstOrNull { sameDirectory(it.config.baseRepositoryPath, repoRoot) }
        } else {
            projects.firstOrNull { it.config.worktreesRoot.isNotBlank() && classifyOrigin(Worktree(path = path, branch = null), it.config) == WorktreeOrigin.SHOGUNAI }
                ?: run {
                    err.line("shogun: hook herdr: couldn't find the repository of $path, so its branch was left alone")
                    return EXIT_OK
                }
        }
        if (project == null || branch == null) return EXIT_OK
        reportKeptBranch(branch, DeleteMergedBranchUseCase(project.config, executor).outcome(branch).getOrThrow())
        return EXIT_OK
    }

    private fun reportKeptBranch(branch: String?, deletion: BranchDeletion?) {
        val reason = deletion?.keptBecause() ?: return
        err.line("shogun: kept branch $branch because $reason")
    }

    private suspend fun flagOutsideWorktreesRoot(worktree: Worktree, config: ProjectConfig) {
        if (config.worktreesRoot.isBlank() || classifyOrigin(worktree, config) == WorktreeOrigin.SHOGUNAI) return
        val body = "${folderName(worktree.path)} is outside ${displayPath(config.worktreesRoot)}; create worktrees from ShogunAi"
        val herdr = environmentVariable(HERDR_BIN_PATH)?.takeIf { it.isNotBlank() } ?: "herdr"
        val failure = try {
            val result = executor.execute(listOf(herdr, "notification", "show", "ShogunAi", "--body", body))
            if (result.isSuccess) null else result.stderr.trim().ifEmpty { "exit code ${result.exitCode}" }
        } catch (error: CommandExecutionException) {
            error.message
        }
        failure?.let { err.line("shogun: hook herdr: couldn't show Herdr's notification: $it") }
    }

    private fun displayPath(path: String): String =
        if (path == userHome || path.startsWith("$userHome/")) "~" + path.removePrefix(userHome) else path

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

    private fun statusLine(entry: WorktreeEntry): String {
        val (worktree, status, origin) = entry
        val details = buildList {
            add(worktree.branch ?: "(detached)")
            if (worktree.isMain) add("main")
            origin?.let { add("origin=${it.label}") }
            if (worktree.isLocked) add("locked")
            entry.agents.takeIf { it.isNotEmpty() }?.let { agents -> add("agents=${agents.joinToString(",") { agentLabel(it) }}") }
            when {
                worktree.isPrunable -> add("folder-missing")
                status == null -> add(entry.statusProblem?.let { "status-unavailable ($it)" } ?: "status-unavailable")
                else -> {
                    add("changes=${status.uncommittedChanges}")
                    status.upstream?.let { add("ahead=${it.ahead} behind=${it.behind}") }
                    if (status.isMerged) add("merged")
                    if (status.isNotStarted) add("not-started")
                }
            }
        }
        return (listOf(worktree.path) + details).joinToString("  ")
    }

    private fun appendToEnvironmentFile(path: String, content: String) {
        try {
            File(path).appendText(content)
        } catch (error: FileNotFoundException) {
            throw FileNotFoundException(error.message.orEmpty().replaceFirst(File(path).path, path))
        }
    }

    private fun folderName(path: String): String =
        path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')

    private fun agentLabel(agent: RunningAgent): String =
        agent.herdrState?.let { "${agent.name}(${it.name.lowercase()})" } ?: agent.name

    private fun absolute(path: String): String =
        if (path.startsWith('/') || File(path).isAbsolute) path else File(workingDirectory, path).toPath().normalize().toString().replace('\\', '/')

    private fun sameDirectory(first: String, second: String): Boolean =
        runCatching { canonicalPath(first) == canonicalPath(second) }.getOrDefault(first == second)

    private fun Appendable.line(text: String) {
        append(text).append('\n')
    }

    private val DaemonKind.label: String
        get() = when (this) {
            DaemonKind.GRADLE -> "gradle"
            DaemonKind.KOTLIN -> "kotlin"
        }

    private val DaemonKeptReason.label: String
        get() = when (this) {
            DaemonKeptReason.BUSY -> "busy"
            DaemonKeptReason.STATE_UNKNOWN -> "state unknown"
            DaemonKeptReason.BUILD_RUNNING -> "a Gradle build is running"
        }

    companion object {
        const val EXIT_OK = 0
        const val EXIT_FAILURE = 1
        const val EXIT_USAGE = 2
        const val EXIT_DRIFT = 3
        private const val CLAUDE_CODE_WORKTREES = ".claude/worktrees"
        private const val CLAUDE_ENV_FILE = "CLAUDE_ENV_FILE"
        private const val HERDR_PLUGIN_EVENT = "HERDR_PLUGIN_EVENT"
        private const val HERDR_PLUGIN_EVENT_JSON = "HERDR_PLUGIN_EVENT_JSON"
        private const val HERDR_WORKTREE_CREATED = "worktree.created"
        private const val HERDR_WORKTREE_REMOVED = "worktree.removed"
        private const val HERDR_BIN_PATH = "HERDR_BIN_PATH"

        const val BUILD_CACHE_NOTICE =
            "Gradle's build cache is off: add org.gradle.caching=true to gradle.properties so a new worktree " +
                "reuses what other worktrees already built."

        fun formatMemory(bytes: Long): String {
            val units = listOf("B", "KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var unit = 0
            while (value >= 1024 && unit < units.lastIndex) {
                value /= 1024
                unit++
            }
            return if (unit == 0) "$bytes B" else String.format(Locale.ROOT, "%.1f %s", value, units[unit])
        }

        fun formatDuration(duration: Duration): String {
            val minutes = duration.toMinutes().coerceAtLeast(0)
            return when {
                minutes < 60 -> "${minutes}m"
                minutes < 24 * 60 -> "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}m"
                else -> "${minutes / (24 * 60)}d"
            }
        }


    }
}
