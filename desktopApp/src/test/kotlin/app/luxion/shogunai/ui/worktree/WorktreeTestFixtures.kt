package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.ScriptedShellCommandExecutor
import app.luxion.shogunai.EmulatorUseCases
import app.luxion.shogunai.GradleUseCases
import app.luxion.shogunai.SimulatorUseCases
import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.XcodeUseCases
import app.luxion.shogunai.domain.FakeClipboardWriter
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.ui.multirepo.MultirepoActions
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.CopyWorktreeLaunchCommandUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DeleteBranchUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.usecase.FetchRemoteUseCase
import app.luxion.shogunai.domain.usecase.ListBranchesUseCase
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.MeasureWorktreeSizeUseCase
import app.luxion.shogunai.domain.usecase.ReadGitMetadataFingerprintUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.ResolveStartPointUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal class FakeWorktreeRepository(
    val id: String = "main-repo",
    var worktrees: List<Pair<String, String>> = listOf("/home/dev/projects/TASK-123" to "feature/TASK-123"),
    var respond: (List<String>) -> CommandResult? = { null },
) {
    val base = "/home/dev/projects/$id"
    val config = ProjectConfig(baseRepositoryPath = base, worktreesRoot = "/home/dev/projects", secretFiles = emptyList())
    val project = Project(id = id, name = id, config = config)
    private val listCommand = listOf("git", "worktree", "list", "--porcelain")

    val porcelain: String
        get() = (listOf(base to "main") + worktrees).joinToString("\n\n") { (path, branch) ->
            if (branch.isEmpty()) "worktree $path\nHEAD abc\ndetached" else "worktree $path\nHEAD abc\nbranch refs/heads/$branch"
        }

    val executor = object : ScriptedShellCommandExecutor() {
        override suspend fun respond(command: List<String>, workingDirectory: String?): CommandResult {
            respond(command)?.let { return it }
            return when {
                command == listCommand -> success(command, porcelain)
                command.getOrNull(1) == "symbolic-ref" -> success(command, "origin/main\n")
                command == listOf("git", "rev-list", "--left-right", "--count", "origin/main...HEAD") -> success(command, "2\t0\n")
                else -> success(command)
            }
        }
    }

    fun useCases(dispatcher: CoroutineDispatcher = Dispatchers.Unconfined): WorktreeUseCases {
        val files = FakeFileManager(existing = listOf(base))
        return WorktreeUseCases(
            list = ListWorktreesUseCase(config, executor),
            create = CreateWorktreeUseCase(config, executor, files, FakeRepositoryProvisioningSource()),
            createFromBranch = CreateWorktreeFromBranchUseCase(config, executor, files, FakeRepositoryProvisioningSource()),
            listBranches = ListBranchesUseCase(config, executor),
            fetchRemote = FetchRemoteUseCase(config, executor),
            resolveStartPoint = { ref -> ResolveStartPointUseCase(executor)(base, ref) },
            remove = RemoveWorktreeUseCase(config, executor),
            deleteBranch = DeleteBranchUseCase(config, executor),
            copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(config, FakeClipboardWriter()),
            repair = RepairWorktreesUseCase(config, executor, files),
            resolveMainBranch = ResolveMainBranchUseCase(config, executor),
            readStatus = ReadWorktreeStatusUseCase(executor, FakeFileManager()),
            cleanUp = CleanUpWorktreesUseCase(config, executor),
            detectAgents = { worktrees -> DetectRunningAgentsUseCase(executor)(config, worktrees) },
            measureSize = MeasureWorktreeSizeUseCase(FakeFileManager(), dispatcher),
            readFingerprint = ReadGitMetadataFingerprintUseCase(config, FakeFileManager(), dispatcher),
            readPorts = { Result.success(emptyMap()) },
            emulator = EmulatorUseCases.NONE,
            simulator = SimulatorUseCases.NONE,
            gradle = GradleUseCases(
                provider = { null },
                listDaemons = ListGradleDaemonsUseCase(FakeGradleDaemonSource(), FakeFileManager()),
                stopDaemons = StopGradleDaemonsUseCase(FakeGradleDaemonSource(), executor),
                isBuildCacheEnabled = { true },
            ),
            xcode = XcodeUseCases(
                provider = { null },
                supported = { false },
                list = ListDerivedDataUseCase(FakeDerivedDataSource()),
                deleteOrphans = DeleteOrphanedDerivedDataUseCase(FakeDerivedDataSource()),
            ),
        )
    }
}

internal fun uiBoard(
    repositories: List<FakeWorktreeRepository>,
    agents: Map<String, List<RunningAgent>> = emptyMap(),
    multirepoActions: MultirepoActions = MultirepoActions.NONE,
    approval: DialogRepositoryApproval = DialogRepositoryApproval(),
    customize: (FakeWorktreeRepository, WorktreeUseCases) -> WorktreeUseCases = { _, useCases -> useCases },
): WorktreeBoard = WorktreeBoard(
    useCasesFor = { config ->
        val repository = repositories.first { it.config.baseRepositoryPath == config.baseRepositoryPath }
        customize(repository, repository.useCases())
    },
    detectAgents = { Result.success(agents) },
    preferences = FakeShellPreferenceRepository(),
    ioDispatcher = Dispatchers.Unconfined,
    multirepoActions = multirepoActions,
    approval = approval,
).also {
    it.syncCatalog(repositories.map { repository -> repository.project })
    it.show(null)
}
