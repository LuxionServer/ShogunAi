package app.luxion.shogunai.cli

import app.luxion.shogunai.BuildInfo
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.GradleDaemonSource
import app.luxion.shogunai.domain.io.KmpTestWiring
import app.luxion.shogunai.domain.io.PortProbe
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.ANDROID_SERIAL
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.DaemonKeptReason
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.GradleDaemon
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_UDID
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.usecase.CheckGradleBuildCacheUseCase
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DeleteMergedBranchUseCase
import app.luxion.shogunai.domain.usecase.DeleteSimulatorsUseCase
import app.luxion.shogunai.domain.usecase.EnvironmentFormat
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.MachineProviderContributions
import app.luxion.shogunai.domain.usecase.effectiveGradleJvmArgs
import app.luxion.shogunai.domain.usecase.PrepareWorktreeEnvironmentUseCase
import app.luxion.shogunai.domain.usecase.ProviderContributions
import app.luxion.shogunai.domain.usecase.ProvisionWorktreeUseCase
import app.luxion.shogunai.domain.usecase.ReadEmulatorStatesUseCase
import app.luxion.shogunai.domain.usecase.ReadSimulatorStatesUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.ResolveRepositoryUseCase
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
import app.luxion.shogunai.domain.usecase.selectCleanupCandidates
import app.luxion.shogunai.domain.usecase.totalResidentMemory
import app.luxion.shogunai.infrastructure.FileKmpTestWiring
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
    private val workingDirectory: String,
    private val out: Appendable,
    private val err: Appendable,
    private val readInput: () -> String = { "" },
    private val canonicalPath: (String) -> String = { File(it).canonicalPath },
    private val now: () -> Instant = Instant::now,
    private val environmentVariable: (String) -> String? = System::getenv,
) {
    private val contributions = MachineProviderContributions(
        appleSimulators,
        ::simulatorSettingsFor,
        gradleJvmArgsFor = { repository -> effectiveGradleJvmArgs(fileManager, gradleUserHome, repository) },
    )
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
            CliCommand.Gradle -> gradle()
            is CliCommand.Env -> env(command)
            is CliCommand.Emulator -> emulator(command)
            is CliCommand.Simulator -> simulator(command)
            is CliCommand.SimulatorGradleInit -> simulatorGradleInit(command)
            CliCommand.HookSessionStart -> hookSessionStart()
            is CliCommand.GradleStop -> gradleStop(command)
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
            CreateWorktreeFromBranchUseCase(config, executor, fileManager, provisioningSource, prepareEnvironment)(command.branch)
        } else {
            CreateWorktreeUseCase(config, executor, fileManager, provisioningSource, prepareEnvironment)(
                command.taskId.orEmpty(),
                command.branchType,
            )
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
        ProvisionWorktreeUseCase(config, executor, fileManager, provisioningSource, prepareEnvironment)(worktree.path).getOrThrow()
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
        val simulators = if (command.deleteSimulators) simulatorsBeforeCleanup(config, candidates) else SimulatorCleanup()
        if (candidates.isEmpty() && simulators.orphans.isEmpty()) {
            out.line("Nothing to clean up.")
            return EXIT_OK
        }
        val daemons = if (command.stopGradleDaemons) daemonsBeforeCleanup(worktrees) else emptyList()
        val emulators = if (command.stopEmulators) emulatorsBeforeCleanup(config, candidates) else emptyMap()
        if (!command.confirmed) {
            candidates.forEach { out.line("would remove ${it.path}${if (it.isPrunable) " (folder missing)" else ""}") }
            idleDaemonsOfRemovedWorktrees(daemons, worktrees, candidates.map { it.path })
                .forEach { out.line("would stop Gradle daemon $it") }
            emulators.values.forEach { out.line("would stop emulator $it") }
            simulators.byWorktree.values.forEach { out.line("would delete simulator ${it.name} (${it.udid})") }
            simulators.orphans.forEach { out.line("would delete orphaned simulator ${it.name} (${it.udid})") }
            out.line("Run 'shogun clean --yes' to remove ${if (candidates.size == 1) "it" else "them"}.")
            return EXIT_OK
        }
        val report = CleanUpWorktreesUseCase(config, executor)(candidates, mainBranch, command.deleteBranches).getOrThrow()
        report.removed.forEach { out.line("removed $it") }
        report.keptBranches.forEach { out.line("kept branch $it (not merged)") }
        report.failures.forEach { err.line("shogun: couldn't remove ${it.path}: ${it.error.message}") }
        val pids = idleDaemonsOfRemovedWorktrees(daemons, worktrees, report.removed)
        if (pids.isNotEmpty()) stopDaemonsAfterCleanup(pids)
        val removedEmulators = report.removed.mapNotNull { emulators[it] }
        if (removedEmulators.isNotEmpty()) stopEmulatorsAfterCleanup(config, removedEmulators)
        if (command.deleteSimulators) {
            val orphansNow = deleteSimulators.orphans(config).getOrDefault(simulators.orphans)
            val removedSimulators = report.removed.mapNotNull { simulators.byWorktree[it]?.udid } + orphansNow.map { it.udid }
            if (removedSimulators.isNotEmpty()) deleteSimulatorsAfterCleanup(removedSimulators.distinct())
        }
        return if (report.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
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

    private suspend fun deleteSimulatorsAfterCleanup(udids: List<String>) {
        deleteSimulators(udids)
            .onSuccess { deleted -> deleted.forEach { out.line("deleted simulator $it") } }
            .onFailure { err.line("shogun: couldn't delete simulators: ${it.message}") }
    }

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
        if (simulator == null) {
            out.line("No simulator for this worktree yet; 'shogun simulator start' creates and boots one.")
        } else {
            out.line("${simulator.udid} ${simulator.state.name.lowercase()}  ${simulator.name}")
        }
        if (command.action == SimulatorAction.STATUS) printKmpWiring(config)
        return EXIT_OK
    }

    private fun printKmpWiring(config: ProjectConfig) {
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

    private suspend fun stopEmulatorsAfterCleanup(config: ProjectConfig, serials: List<String>) {
        runCatching { stopEmulator.stopSerials(config, serials) }
            .onSuccess { stopped -> stopped.forEach { out.line("stopped emulator ${it.serial}") } }
            .onFailure { err.line("shogun: couldn't stop emulators: ${it.message}") }
    }

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

    private suspend fun stopDaemonsAfterCleanup(pids: List<Long>) {
        StopGradleDaemonsUseCase(daemonSource, executor)(pids)
            .onSuccess { report ->
                printStopReport(report)
                report.failures.forEach { err.line("shogun: couldn't stop Gradle daemon ${it.pid}: ${it.detail}") }
            }
            .onFailure { err.line("shogun: couldn't stop Gradle daemons: ${it.message}") }
    }

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
            File(path).appendText(formatEnvironment(environment, EnvironmentFormat.SH))
        }
        val variables = environment.variables.joinToString(", ") { (name, value) -> "$name=$value" }
        val emulatorHint = environment.variables.toMap()[ANDROID_SERIAL]
            ?.takeIf { provisioningSource.read(repository).providers.emulator != null }
            ?.let { serial -> ". Its Android emulator is $ANDROID_SERIAL=$serial; start it with 'shogun emulator start' before installing the app or running instrumented tests" }
            .orEmpty()
        val simulatorHint = environment.variables.toMap()[IOS_SIMULATOR_UDID]
            ?.takeIf { provisioningSource.read(repository).providers.iosSimulator != null }
            ?.let { udid ->
                ". Its iOS simulator is $IOS_SIMULATOR_UDID=$udid; boot it with 'shogun simulator start' and build with " +
                    "xcodebuild -destination \"id=\$$IOS_SIMULATOR_UDID\""
            }
            .orEmpty()
        out.line("ShogunAi environment of worktree ${File(worktree.path).name}: $variables$emulatorHint$simulatorHint")
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

    private suspend fun gradle(): Int {
        val repository = ResolveRepositoryUseCase(executor)(workingDirectory).getOrNull()
        val worktrees = repository?.let { ListWorktreesUseCase(configFor(workingDirectory), executor)().getOrNull() }
        val daemons = ListGradleDaemonsUseCase(daemonSource, fileManager)(worktrees).getOrThrow()
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
        if (command.pids.isEmpty() && report.stopped.isEmpty() && report.kept.isEmpty() && report.failures.isEmpty()) {
            out.line("No Gradle or Kotlin daemons running.")
        }
        printStopReport(report)
        report.notDaemons.forEach { err.line("shogun: $it isn't a Gradle or Kotlin daemon") }
        report.failures.forEach { err.line("shogun: couldn't stop ${it.pid}: ${it.detail}") }
        return if (report.notDaemons.isEmpty() && report.failures.isEmpty()) EXIT_OK else EXIT_FAILURE
    }

    private fun printStopReport(report: DaemonStopReport) {
        report.stopped.forEach { out.line("stopped ${it.pid}  ${kindLabel(it)}") }
        report.kept.forEach { out.line("kept ${it.pid}  ${it.kind.label}  (${it.reason.label})") }
    }

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
            projectConfig.copy(worktreesRoot = File(projectConfig.baseRepositoryPath, CLAUDE_CODE_WORKTREES).path)
        } else {
            projectConfig
        }
        val target = config.worktreePathFor(normalizeTaskId(name))
        val existing = ListWorktreesUseCase(config, executor)().getOrThrow()
            .firstOrNull { !it.isMain && sameDirectory(it.path, target) }
        val path = existing?.path
            ?: CreateWorktreeUseCase(config, executor, fileManager, provisioningSource, prepareEnvironment)(name, BranchType.FEATURE).getOrThrow().path
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
        private const val CLAUDE_CODE_WORKTREES = ".claude/worktrees"
        private const val CLAUDE_ENV_FILE = "CLAUDE_ENV_FILE"

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

        val USAGE = """
            Usage: shogun <command> [options]

            Commands:
              new <task-id> [--fix]       Create a worktree on a new feature/ (or fix/) branch and print its path
              new --branch <branch>       Create a worktree for an existing local branch and print its path
                  --worktrees-root <dir>  Folder for the worktree when the repository has no ShogunAi project
              provision [<path>]          Copy the declared files into an existing worktree (default: current directory)
              status                      List the repository's worktrees with their state
              clean [--yes] [--delete-branches] [--stop-gradle-daemons] [--stop-emulators] [--delete-simulators]
                                          List merged or missing worktrees; remove them with --yes
                                          (--stop-gradle-daemons / --stop-emulators: also stop their idle
                                          Gradle daemons / their emulators; --delete-simulators: also delete
                                          their iOS simulators and orphaned ShogunAi simulators)
              gradle                      List the Gradle and Kotlin daemons with their memory, state and worktree
              gradle stop [<pid>...]      Stop idle daemons (all of them, or only the given ones); busy ones are kept
              hook worktree-create       Claude Code WorktreeCreate hook: create and provision the worktree, print its path
              hook worktree-remove        Claude Code WorktreeRemove hook: remove the worktree, delete its branch if merged
              hook session-start          Claude Code SessionStart hook: export the worktree's environment to the session
              simulator start|stop|status [<path>] [--device <type>] [--runtime <runtime>]
                                          Boot (and wait for), shut down or show the worktree's iOS simulator
                                          (repositories with "providers": { "ios-simulator": {} } in .shogunai.json)
              simulator gradle-init install|uninstall [--yes]
                                          Make every project's KMP iOS simulator tests use IOS_SIMULATOR_UDID
              emulator start|stop|status [<path>] [--avd <name>] [--no-window]
                                          Start (and wait for), stop or show the worktree's Android emulator
                                          (repositories with "providers": { "emulator": {} } in .shogunai.json)
              env [<path>] [--format sh|dotenv|json]
                                          Print the worktree's environment from .shogunai.json "env"
                                          (sh prints export lines for: eval "$(shogun env)")
              help                        Show this help
              --version                   Show the version

            Works on the Git repository containing the current directory. Files to copy come from the
            repository's .shogunai.json and .worktreeinclude, and from the desktop app's project if there is one.

        """.trimIndent() + "\n"
    }
}
