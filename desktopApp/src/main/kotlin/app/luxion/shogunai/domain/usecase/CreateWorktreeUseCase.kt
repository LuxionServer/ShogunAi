package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.joinPath

/**
 * Crea un worktree y lo deja listo para compilar el proyecto Android.
 *
 * Orquesta, en secuencia:
 *  1. Validaciones previas (repo base existe, destino libre, secretos presentes).
 *  2. `git worktree add <path> -b <branch>`.
 *  3. Copia de los archivos de secretos al nuevo directorio.
 *
 * Las validaciones se hacen *antes* de tocar Git para no dejar a medias un
 * worktree que luego no podríamos completar. Si la copia falla tras crear el
 * worktree, se revierte con un `git worktree remove --force` (best-effort).
 */
class CreateWorktreeUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
) {
    suspend operator fun invoke(taskId: String, branchType: BranchType): Result<Worktree> =
        runCatching {
            val id = taskId.trim()
            require(id.isNotEmpty()) { "El identificador de la tarea no puede estar vacío" }

            if (!fileManager.exists(config.baseRepositoryPath)) {
                throw WorktreeError.BaseRepositoryNotFound(config.baseRepositoryPath)
            }

            val worktreePath = config.worktreePathFor(id)
            if (fileManager.exists(worktreePath)) {
                throw WorktreeError.WorktreeAlreadyExists(worktreePath)
            }

            val missingSecrets = config.secretFiles.filterNot { fileManager.exists(config.baseSecretPath(it)) }
            if (missingSecrets.isNotEmpty()) {
                throw WorktreeError.SecretFileNotFound(missingSecrets, config.baseRepositoryPath)
            }

            val branch = "${branchType.prefix}/$id"
            val add = executor.execute(
                command = listOf("git", "worktree", "add", worktreePath, "-b", branch),
                workingDirectory = config.baseRepositoryPath,
            )
            if (!add.isSuccess) {
                if (add.stderr.contains(GIT_LFS_MISSING_MARKER)) {
                    // El hook post-checkout de Git LFS ya dejó el worktree y la rama
                    // creados en disco antes de fallar: revertimos ambos para que un
                    // reintento no choque ni con el directorio ni con la rama existentes.
                    executor.execute(
                        command = listOf("git", "worktree", "remove", "--force", worktreePath),
                        workingDirectory = config.baseRepositoryPath,
                    )
                    executor.execute(
                        command = listOf("git", "branch", "-D", branch),
                        workingDirectory = config.baseRepositoryPath,
                    )
                    throw WorktreeError.GitLfsNotFound
                }
                throw WorktreeError.GitCommandFailed(add.command, add.exitCode, add.stderr)
            }

            copySecretsOrRollback(worktreePath)

            Worktree(path = worktreePath, branch = branch)
        }

    private suspend fun copySecretsOrRollback(worktreePath: String) {
        try {
            config.secretFiles.forEach { fileName ->
                fileManager.copy(
                    source = config.baseSecretPath(fileName),
                    destination = joinPath(worktreePath, fileName),
                )
            }
        } catch (copyError: Exception) {
            // El worktree ya existe pero está incompleto: lo eliminamos para no
            // dejar entornos a medias. Ignoramos el resultado del rollback.
            executor.execute(
                command = listOf("git", "worktree", "remove", "--force", worktreePath),
                workingDirectory = config.baseRepositoryPath,
            )
            throw WorktreeError.SecretCopyFailed(copyError)
        }
    }

    private companion object {
        const val GIT_LFS_MISSING_MARKER = "git-lfs' was not found on your path"
    }
}
