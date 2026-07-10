package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

/**
 * Elimina un worktree y, opcionalmente, su rama local.
 *
 * Git no permite eliminar un worktree desde dentro de sí mismo, por lo que el
 * comando se ejecuta siempre desde el repositorio base.
 */
class RemoveWorktreeUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    /**
     * @param worktreePath Ruta del worktree a eliminar.
     * @param branchToDelete Rama local a borrar tras eliminar el worktree, o
     *   `null` para conservarla. Se usa `git branch -d` (borrado seguro).
     * @param force Añade `--force` a `git worktree remove` para eliminar aunque
     *   haya cambios sin guardar.
     */
    suspend operator fun invoke(
        worktreePath: String,
        branchToDelete: String? = null,
        force: Boolean = false,
    ): Result<Unit> = runCatching {
        val removeCommand = buildList {
            addAll(listOf("git", "worktree", "remove"))
            if (force) add("--force")
            add(worktreePath)
        }
        val remove = executor.execute(removeCommand, workingDirectory = config.baseRepositoryPath)
        if (!remove.isSuccess) {
            throw WorktreeError.GitCommandFailed(remove.command, remove.exitCode, remove.stderr)
        }

        if (branchToDelete != null) {
            val deleteBranch = executor.execute(
                command = listOf("git", "branch", "-d", branchToDelete),
                workingDirectory = config.baseRepositoryPath,
            )
            if (!deleteBranch.isSuccess) {
                throw WorktreeError.GitCommandFailed(
                    deleteBranch.command,
                    deleteBranch.exitCode,
                    deleteBranch.stderr,
                )
            }
        }
    }
}
