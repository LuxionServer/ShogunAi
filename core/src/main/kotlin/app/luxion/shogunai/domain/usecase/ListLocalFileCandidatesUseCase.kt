package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.GENERATED_FOLDERS
import app.luxion.shogunai.domain.model.LocalFileCandidate
import app.luxion.shogunai.domain.model.MobileSecretKind
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

class ListLocalFileCandidatesUseCase(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
) {
    suspend operator fun invoke(config: ProjectConfig): List<LocalFileCandidate> {
        val entries = (untrackedEntries(config, ignored = false) + untrackedEntries(config, ignored = true))
            .distinct()
            .filterNot(::isGenerated)
        val provided = repositoryFiles(config)
        return entries.map { entry ->
            val path = entry.removeSuffix("/")
            val isFolder = entry.endsWith("/")
            LocalFileCandidate(
                path = path,
                isFolder = isFolder,
                secretKind = if (isFolder) null else MobileSecretKind.of(path.substringAfterLast('/')),
                providedByRepository = if (isFolder) isFolderProvided(config, path, provided) else path in provided,
            )
        }
    }

    private suspend fun untrackedEntries(config: ProjectConfig, ignored: Boolean): List<String> {
        val command = listOf("git", "ls-files", "-z", "--others", "--exclude-standard", "--directory", "--no-empty-directory") +
            listOfNotNull("--ignored".takeIf { ignored })
        val result = executor.execute(command = command, workingDirectory = config.baseRepositoryPath)
        if (!result.isSuccess) throw WorktreeError.GitCommandFailed(result.command, result.exitCode, result.stderr)
        return result.stdout.split('\u0000').filter { it.isNotEmpty() }
    }

    private suspend fun repositoryFiles(config: ProjectConfig): Set<String> {
        val provisioning = provisioningSource.read(config.baseRepositoryPath)
        val patterns = (provisioning.allPatterns + provisioning.linkPatterns).distinct()
        return listUntrackedFiles(config, executor, patterns).toSet()
    }

    private fun isFolderProvided(config: ProjectConfig, folder: String, provided: Set<String>): Boolean {
        if (provided.isEmpty()) return false
        val files = fileManager.listTree("${config.baseRepositoryPath}/$folder").files
        return files.isNotEmpty() && files.all { "$folder/$it" in provided }
    }

    private fun isGenerated(entry: String): Boolean = entry.removeSuffix("/").split('/').let { segments ->
        val folders = if (entry.endsWith("/")) segments else segments.dropLast(1)
        folders.any { it in GENERATED_FOLDERS }
    }
}
