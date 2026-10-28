package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.PodsInstallNeeded
import app.luxion.shogunai.domain.model.PodsInstallReason
import app.luxion.shogunai.domain.model.PodsPreparation
import app.luxion.shogunai.domain.model.joinPath
import kotlinx.coroutines.CancellationException

fun interface CocoaPodsPreparer {
    suspend fun prepare(repositoryPath: String, worktreePath: String): PodsPreparation

    companion object {
        val NONE = CocoaPodsPreparer { _, _ -> PodsPreparation() }
    }
}

class PrepareCocoaPodsUseCase(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
    private val isMacOs: Boolean,
) : CocoaPodsPreparer {

    override suspend fun prepare(repositoryPath: String, worktreePath: String): PodsPreparation = try {
        if (!isMacOs || provisioningSource.read(repositoryPath).providers.xcode?.cocoaPods != true) {
            PodsPreparation()
        } else {
            preparePodFolders(repositoryPath, worktreePath)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        PodsPreparation(error = error.message ?: error::class.simpleName)
    }

    private suspend fun preparePodFolders(repositoryPath: String, worktreePath: String): PodsPreparation {
        val cloned = mutableListOf<String>()
        val installNeeded = mutableListOf<PodsInstallNeeded>()
        trackedPodFolders(worktreePath).forEach { folder ->
            val worktreePods = podsPath(worktreePath, folder)
            if (fileManager.exists(worktreePods)) return@forEach
            when (val reason = clonePods(repositoryPath, worktreePath, folder)) {
                null -> cloned += folder
                else -> installNeeded += PodsInstallNeeded(folder, reason)
            }
        }
        return PodsPreparation(cloned = cloned, installNeeded = installNeeded)
    }

    private suspend fun trackedPodFolders(worktreePath: String): List<String> {
        val result = executor.execute(
            command = listOf("git", "ls-files", "-z", "--", ":(glob)**/$PODFILE_LOCK"),
            workingDirectory = worktreePath,
        )
        if (!result.isSuccess) throw IllegalStateException(result.stderr.trim().ifEmpty { "git ls-files failed" })
        return result.stdout.split('\u0000')
            .filter { it == PODFILE_LOCK || it.endsWith("/$PODFILE_LOCK") }
            .map { it.removeSuffix(PODFILE_LOCK).trimEnd('/').ifEmpty { ROOT_FOLDER } }
            .distinct()
    }

    private suspend fun clonePods(repositoryPath: String, worktreePath: String, folder: String): PodsInstallReason? {
        val basePods = podsPath(repositoryPath, folder)
        val manifest = joinPath(basePods, MANIFEST_LOCK)
        if (!fileManager.exists(manifest)) return PodsInstallReason.BASE_HAS_NO_PODS
        if (!fileManager.hasSameContent(manifest, joinPath(folderPath(worktreePath, folder), PODFILE_LOCK))) {
            return PodsInstallReason.BASE_PODS_DIFFER
        }
        val worktreePods = podsPath(worktreePath, folder)
        val clone = executor.execute(command = listOf("cp", "-c", "-R", basePods, worktreePods))
        if (clone.isSuccess) return null
        runCatching { fileManager.deleteRecursively(worktreePods) }
        return PodsInstallReason.CLONE_FAILED
    }

    private fun folderPath(root: String, folder: String): String =
        if (folder == ROOT_FOLDER) root else joinPath(root, folder)

    private fun podsPath(root: String, folder: String): String = joinPath(folderPath(root, folder), PODS)

    private companion object {
        const val PODFILE_LOCK = "Podfile.lock"
        const val MANIFEST_LOCK = "Manifest.lock"
        const val PODS = "Pods"
        const val ROOT_FOLDER = "."
    }
}
