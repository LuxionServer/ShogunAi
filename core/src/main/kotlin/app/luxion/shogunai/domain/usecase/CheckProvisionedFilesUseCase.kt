package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.ProvisioningRecordStore
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ProvisionDrift
import app.luxion.shogunai.domain.model.ProvisionDriftState
import app.luxion.shogunai.domain.model.joinPath

class CheckProvisionedFilesUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
    private val records: ProvisioningRecordStore = ProvisioningRecordStore.NONE,
) {
    suspend operator fun invoke(worktreePath: String): Result<List<ProvisionDrift>> = runCatching {
        val worktree = findLinkedWorktree(config, executor, worktreePath)
        val provisioned = checkProvisioningAndResolveFiles(config, executor, fileManager, provisioningSource)
        val copiedDrift = provisioned.copied.mapNotNull { fileName ->
            val destination = joinPath(worktree.path, fileName)
            when {
                !fileManager.exists(destination) -> ProvisionDrift(fileName, ProvisionDriftState.MISSING)
                !fileManager.hasSameContent(config.baseSecretPath(fileName), destination) ->
                    ProvisionDrift(fileName, ProvisionDriftState.DIFFERS)
                else -> null
            }
        }
        val linkedDrift = provisioned.linked.mapNotNull { fileName ->
            val destination = joinPath(worktree.path, fileName)
            when {
                !fileManager.exists(destination) -> ProvisionDrift(fileName, ProvisionDriftState.MISSING)
                !fileManager.isSymbolicLinkTo(destination, config.baseSecretPath(fileName)) ->
                    ProvisionDrift(fileName, ProvisionDriftState.NOT_LINKED)
                else -> null
            }
        }
        val linkedFolderDrift = provisioned.linkedFolders.keys.mapNotNull { folder ->
            val destination = joinPath(worktree.path, folder)
            when {
                !fileManager.exists(destination) -> ProvisionDrift(folder, ProvisionDriftState.MISSING)
                !fileManager.isSymbolicLinkTo(destination, config.baseSecretPath(folder)) ->
                    ProvisionDrift(folder, ProvisionDriftState.NOT_LINKED)
                else -> null
            }
        }
        val previous = records.read(worktree.path)
        val deletedDrift = provisioned.copiedFolders.flatMap { (folder, files) -> deletedFromBase(folder, files, previous) }
            .filter { fileManager.exists(joinPath(worktree.path, it)) }
            .map { ProvisionDrift(it, ProvisionDriftState.DELETED_IN_BASE) }
        copiedDrift + linkedDrift + linkedFolderDrift + deletedDrift
    }
}
