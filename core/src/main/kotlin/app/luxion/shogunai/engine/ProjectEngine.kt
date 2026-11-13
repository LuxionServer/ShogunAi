package app.luxion.shogunai.engine

import app.luxion.shogunai.domain.io.RepositoryApproval
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.usecase.CheckProvisionedFilesUseCase
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.CloseHerdrWorkspacesUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DeleteBranchUseCase
import app.luxion.shogunai.domain.usecase.FetchRemoteUseCase
import app.luxion.shogunai.domain.usecase.ListBranchesUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ProvisionWorktreeUseCase
import app.luxion.shogunai.domain.usecase.ReadBaseBranchesUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.ResolveRecordedBasesUseCase
import app.luxion.shogunai.domain.usecase.SetBaseBranchUseCase

class ProjectEngine(
    val config: ProjectConfig,
    private val engine: ShogunEngine,
) {
    private val ports = engine.ports
    private val executor = ports.executor
    private val fileManager = ports.fileManager

    val listWorktrees = ListWorktreesUseCase(config, executor, fileManager)
    val listBranches = ListBranchesUseCase(config, executor)
    val fetchRemote = FetchRemoteUseCase(config, executor)
    val resolveMainBranch = ResolveMainBranchUseCase(config, executor)
    val resolveRecordedBases = ResolveRecordedBasesUseCase(config, executor)
    val readBaseBranches = ReadBaseBranchesUseCase(config, executor)
    val setBaseBranch = SetBaseBranchUseCase(config, executor)
    val deleteBranch = DeleteBranchUseCase(config, executor)
    val repair = RepairWorktreesUseCase(config, executor, fileManager)
    val closeHerdrWorkspaces = CloseHerdrWorkspacesUseCase(config, ports.herdr)
    val checkProvisioned = CheckProvisionedFilesUseCase(config, executor, fileManager, ports.provisioningSource, ports.provisioningRecords)

    val remove = RemoveWorktreeUseCase(
        config,
        executor,
        closeHerdrWorkspaces,
        fileManager,
        engine.stopHeadlessApp,
        engine.releaseDesktopTurn,
        engine.closeOrcaTerminals,
        engine.releasePhysicalDevice,
        engine.releaseCustomPool,
    )

    val cleanUp = CleanUpWorktreesUseCase(
        config,
        executor,
        closeHerdrWorkspaces,
        ports.composeProjects,
        fileManager,
        ports.composeRegistry,
        engine.stopHeadlessApp,
        engine.releaseDesktopTurn,
        engine.closeOrcaTerminals,
        engine.releasePhysicalDevice,
        engine.releaseCustomPool,
    )

    fun create(approval: RepositoryApproval): CreateWorktreeUseCase = CreateWorktreeUseCase(
        config, executor, fileManager, ports.provisioningSource, engine.prepareEnvironment, ports.trust, approval, ports.provisioningRecords,
    )

    fun createFromBranch(approval: RepositoryApproval): CreateWorktreeFromBranchUseCase = CreateWorktreeFromBranchUseCase(
        config, executor, fileManager, ports.provisioningSource, engine.prepareEnvironment, ports.trust, approval, ports.provisioningRecords,
    )

    fun provision(approval: RepositoryApproval): ProvisionWorktreeUseCase = ProvisionWorktreeUseCase(
        config, executor, fileManager, ports.provisioningSource, engine.prepareEnvironment, ports.trust, approval, ports.provisioningRecords,
    )
}
