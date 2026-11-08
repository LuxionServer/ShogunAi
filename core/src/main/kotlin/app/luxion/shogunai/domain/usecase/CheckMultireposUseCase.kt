package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.MultirepoSource
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.MultirepoProblem
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.CancellationException

class CheckMultireposUseCase(
    private val source: MultirepoSource,
    private val resolve: ResolveMultirepoUseCase,
    private val detectGroups: DetectGroupsUseCase,
    private val projects: ProjectRepository,
) {

    suspend operator fun invoke(): List<InstallationCheck> = source.knownFolders().map { check(normalizePath(it)) }

    private suspend fun check(folder: String): InstallationCheck {
        val unread = Multirepo(folder, MultirepoDeclaration())
        val declaration = try {
            source.read(folder)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return result(unread, emptyList(), 0, listOf(MultirepoProblem.InvalidDeclaration(unread.declarationPath, error.message)))
        } ?: return result(unread, emptyList(), 0, listOf(MultirepoProblem.DeclarationMissing))
        val multirepo = resolve.build(folder, declaration)
        val problems = mutableListOf<MultirepoProblem>()
        try {
            resolve.validateDeclaration(multirepo)
        } catch (error: WorktreeError.InvalidMultirepo) {
            problems += MultirepoProblem.InvalidDeclaration(error.path, error.detail)
        }
        val missing = resolve.missingRepositories(multirepo)
        problems += missing.map { MultirepoProblem.RepositoryMissing(it.alias, it.path) }
        if (!multirepo.approved) problems += MultirepoProblem.NotTrusted
        val registered = try {
            projects.list()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            emptyList()
        }
        val members = multirepoMembers(multirepo, registered).filter { member -> missing.none { it.alias == member.alias } }
        val worktrees = detectGroups(members)
        problems += worktrees.failures.map { (alias, error) -> MultirepoProblem.ListingFailed(alias, error.message) }
        problems += worktrees.groups.flatMap { group ->
            group.worktrees.filter { it.worktree.isPrunable }.map { MultirepoProblem.GroupWorktreeMissing(group.branch, it.alias, it.worktree.path) }
        }
        return result(multirepo, multirepo.repositories.map { it.alias }, worktrees.groups.size, problems)
    }

    private fun result(multirepo: Multirepo, repositories: List<String>, groups: Int, problems: List<MultirepoProblem>): InstallationCheck {
        val state = when {
            problems.any { it.blocking } -> CheckState.FAILED
            problems.isNotEmpty() -> CheckState.WARNING
            else -> CheckState.PASSED
        }
        return InstallationCheck(state, InstallationFinding.MultirepoCheck(multirepo.folder, multirepo.name, repositories, groups, problems))
    }
}
