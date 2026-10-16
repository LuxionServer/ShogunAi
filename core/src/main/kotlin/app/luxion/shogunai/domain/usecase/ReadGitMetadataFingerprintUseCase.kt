package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.ProjectConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReadGitMetadataFingerprintUseCase(
    private val config: ProjectConfig,
    private val fileManager: FileManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend operator fun invoke(): Result<List<Pair<String, Long>>> = runCatching {
        withContext(ioDispatcher) {
            val gitDir = "${normalizePath(config.baseRepositoryPath)}/.git"
            if (!fileManager.isDirectory(gitDir)) return@withContext emptyList()
            WATCHED_ENTRIES
                .flatMap { entry -> fileManager.modificationStamps("$gitDir/$entry") }
                .map { (path, stamp) -> normalizePath(path).removePrefix("$gitDir/") to stamp }
                .sortedBy { it.first }
        }
    }

    private companion object {
        val WATCHED_ENTRIES = listOf("HEAD", "index", "packed-refs", "FETCH_HEAD", "refs", "worktrees")
    }
}
