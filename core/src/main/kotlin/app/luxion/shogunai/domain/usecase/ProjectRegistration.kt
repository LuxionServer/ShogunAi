package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.newProject
import java.util.UUID

sealed interface ProjectRegistration {
    val project: Project

    data class Added(override val project: Project) : ProjectRegistration
    data class AlreadyRegistered(override val project: Project) : ProjectRegistration
}

class RegisterProjectUseCase(
    private val projects: ProjectRepository,
    private val resolveRepository: ResolveRepositoryUseCase,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val workspaceOf: (String) -> Multirepo? = { null },
) {
    suspend operator fun invoke(directory: String): Result<ProjectRegistration> = runCatching {
        val repository = resolveRepository(directory).getOrThrow()
        val existing = projects.list().firstOrNull { sameDirectory(it.config.baseRepositoryPath, repository) }
        if (existing != null) return@runCatching ProjectRegistration.AlreadyRegistered(existing)
        val workspace = workspaceOf(repository)
        val created = newProject(newId(), repository)
        val workspaceRepository = workspace?.workspaceRepository
        val project = if (workspace == null || workspaceRepository == null) {
            created
        } else {
            created.copy(config = created.config.copy(worktreesRoot = workspace.proposedWorktreesFolder(workspaceRepository.alias)))
        }
        projects.save(project)
        ProjectRegistration.Added(project)
    }
}

class UnregisterProjectUseCase(
    private val projects: ProjectRepository,
    private val resolveRepository: ResolveRepositoryUseCase,
) {
    suspend operator fun invoke(directory: String): Result<Project> = runCatching {
        val repository = resolveRepository(directory).getOrNull() ?: directory
        val project = projects.list().firstOrNull { sameDirectory(it.config.baseRepositoryPath, repository) }
            ?: throw WorktreeError.ProjectNotRegistered(repository)
        projects.delete(project.id)
        project
    }
}

fun List<Multirepo>.withWorkspaceAt(directory: String): Multirepo? =
    firstOrNull { it.workspaceRepository != null && sameDirectory(it.folder, directory) }
