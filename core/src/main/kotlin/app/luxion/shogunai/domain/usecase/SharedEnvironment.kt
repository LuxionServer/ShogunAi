package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.EnvironmentVariable
import app.luxion.shogunai.domain.model.InvalidSharedEnvironment
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.UnresolvedReason
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.repositoryPorts

sealed interface SharedTarget {
    data class Located(val repositoryPath: String, val worktreePath: String) : SharedTarget
    data class Unreadable(val detail: String) : SharedTarget
}

data class SharedEnvironment(
    val variables: List<EnvironmentVariable> = emptyList(),
    val targets: Map<String, SharedTarget> = emptyMap(),
    val invalid: UnresolvedReason.InvalidDeclaration? = null,
    val invalidNames: List<String> = emptyList(),
)

fun interface SharedEnvironmentSource {
    suspend fun forWorktree(repositoryPath: String, worktreePath: String): SharedEnvironment?

    companion object {
        val NONE = SharedEnvironmentSource { _, _ -> null }
    }
}

class MultirepoSharedEnvironment(
    private val resolveMultirepo: ResolveMultirepoUseCase,
    private val executor: ShellCommandExecutor,
) : SharedEnvironmentSource {

    override suspend fun forWorktree(repositoryPath: String, worktreePath: String): SharedEnvironment? {
        val multirepo = resolveMultirepo.declaring(repositoryPath) ?: return null
        val alias = multirepo.repositories.firstOrNull { sameDirectory(it.path, repositoryPath) }?.alias ?: return null
        val variables = try {
            multirepo.sharedVariables()[alias].orEmpty()
        } catch (error: InvalidSharedEnvironment) {
            return invalid(multirepo, alias, error)
        }
        if (variables.isEmpty()) return null
        val referenced = variables.flatMap { it.repositoryPorts }.map { it.alias }.distinct()
        if (referenced.isEmpty()) return SharedEnvironment(variables)
        val branch = linkedBranchOf(repositoryPath, worktreePath)
        val targets = referenced.associateWith { target -> locate(multirepo.repositories.first { it.alias == target }.path, branch) }
        return SharedEnvironment(variables, targets)
    }

    private fun invalid(multirepo: Multirepo, alias: String, error: InvalidSharedEnvironment): SharedEnvironment? {
        val names = multirepo.declaration.env?.byAlias?.get(alias)?.keys.orEmpty().sorted()
        if (names.isEmpty()) return null
        return SharedEnvironment(
            invalid = UnresolvedReason.InvalidDeclaration(multirepo.declarationPath, error.message.orEmpty()),
            invalidNames = names,
        )
    }

    private suspend fun linkedBranchOf(repositoryPath: String, worktreePath: String): String? =
        worktreesOf(repositoryPath).getOrNull()
            ?.firstOrNull { sameDirectory(it.path, worktreePath) }
            ?.takeUnless { it.isMain }
            ?.branch

    private suspend fun locate(repositoryPath: String, branch: String?): SharedTarget =
        worktreesOf(repositoryPath).fold(
            onSuccess = { worktrees ->
                val partner = branch?.let { wanted -> worktrees.firstOrNull { !it.isMain && !it.isBare && it.branch == wanted } }
                SharedTarget.Located(repositoryPath, partner?.path ?: repositoryPath)
            },
            onFailure = { SharedTarget.Unreadable(it.message.orEmpty()) },
        )

    private suspend fun worktreesOf(repositoryPath: String): Result<List<Worktree>> =
        ListWorktreesUseCase(ProjectConfig(baseRepositoryPath = repositoryPath, worktreesRoot = "", secretFiles = emptyList()), executor)()
}
