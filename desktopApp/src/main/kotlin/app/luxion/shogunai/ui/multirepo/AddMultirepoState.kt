package app.luxion.shogunai.ui.multirepo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.isValidMultirepoAlias
import app.luxion.shogunai.domain.usecase.MultirepoMember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

data class RepositoryCandidate(
    val path: String,
    val alias: String,
    val included: Boolean = true,
    val project: Project? = null,
    val useProposedFolder: Boolean = project == null,
) {
    val name: String get() = File(path).name
}

data class DetectedGroup(val branch: String, val aliases: List<String>)

enum class AliasProblem { INVALID, DUPLICATED }

class AddMultirepoState(private val setup: MultirepoSetup, private val scope: CoroutineScope) {
    var folder by mutableStateOf<String?>(null)
        private set
    var enclosingRepository by mutableStateOf<String?>(null)
        private set
    var candidates by mutableStateOf<List<RepositoryCandidate>>(emptyList())
        private set
    var groups by mutableStateOf<List<DetectedGroup>>(emptyList())
        private set
    var isInspecting by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    var notRepository by mutableStateOf<String?>(null)
        private set
    var existing by mutableStateOf<ExistingMultirepo?>(null)
        private set
    private var detection: Job? = null

    private val folderName: String
        get() = folder?.let { Multirepo(it, MultirepoDeclaration()).folderName }.orEmpty()

    val name: String
        get() = existing?.multirepo?.name ?: folderName

    val worktreesRoot: String
        get() = existing?.multirepo?.worktreesRoot
            ?: folder?.let { multirepoPath(File(File(it).parent ?: "/", "$folderName-worktrees").path) }.orEmpty()

    fun proposedFolder(candidate: RepositoryCandidate): String = multirepoPath(File(worktreesRoot, candidate.alias).path)

    val isExisting: Boolean
        get() = existing != null

    val included: List<RepositoryCandidate>
        get() = candidates.filter { it.included }

    val aliasProblems: Map<String, AliasProblem>
        get() {
            val counts = included.groupingBy { it.alias }.eachCount()
            return included.mapNotNull { candidate ->
                when {
                    !isValidMultirepoAlias(candidate.alias) -> candidate.path to AliasProblem.INVALID
                    (counts[candidate.alias] ?: 0) > 1 -> candidate.path to AliasProblem.DUPLICATED
                    else -> null
                }
            }.toMap()
        }

    val canConfirm: Boolean
        get() {
            if (folder == null || enclosingRepository != null || isInspecting || isSaving) return false
            val existing = existing ?: return included.isNotEmpty() && aliasProblems.isEmpty()
            return existing.multirepo != null && existing.problem == null
        }

    fun choose(folder: String) {
        this.folder = folder
        enclosingRepository = null
        candidates = emptyList()
        groups = emptyList()
        error = null
        notRepository = null
        existing = null
        isInspecting = true
        scope.launch {
            try {
                val inspection = setup.inspect(folder)
                enclosingRepository = inspection.enclosingRepository
                existing = inspection.existing
                val declared = inspection.existing?.multirepo
                when {
                    declared != null -> declared.repositories.forEach { addDeclared(it.alias, it.path) }
                    inspection.enclosingRepository == null && inspection.existing == null -> inspection.repositories.forEach(::addCandidate)
                }
            } catch (failure: Exception) {
                error = failure
            } finally {
                isInspecting = false
            }
            detectGroups()
        }
    }

    fun addFromElsewhere(path: String) {
        if (isExisting) return
        if (candidates.any { sameFolder(it.path, path) }) return
        notRepository = null
        scope.launch {
            if (setup.isRepository(path)) {
                addCandidate(path)
                detectGroups()
            } else {
                notRepository = path
            }
        }
    }

    fun setIncluded(path: String, included: Boolean) {
        if (!isExisting) update(path) { it.copy(included = included) }
    }

    fun setAlias(path: String, alias: String) {
        if (!isExisting) update(path) { it.copy(alias = alias.trim().lowercase()) }
    }

    fun setUseProposedFolder(path: String, use: Boolean) = update(path) { it.copy(useProposedFolder = use) }

    fun confirm(onAdded: (String) -> Unit) {
        val folder = folder ?: return
        if (!canConfirm) return
        val chosen = included
        isSaving = true
        error = null
        val existing = existing
        if (existing != null) {
            confirmExisting(folder, existing, chosen, onAdded)
            return
        }
        scope.launch {
            try {
                var multirepo = setup.read(folder) ?: setup.init(folder).getOrThrow()
                chosen.filter { it.alias !in multirepo.declaration.repos }.forEach { candidate ->
                    setup.addRepository(multirepo, candidate.alias, candidate.path, candidate.useProposedFolder).getOrThrow()
                    multirepo = setup.read(folder) ?: multirepo
                }
                if (multirepo.declaration.defaultRepos.isEmpty()) setup.setDefaults(multirepo, chosen.map { it.alias }).getOrThrow()
                onAdded(multirepo.folder)
            } catch (failure: Exception) {
                error = failure
            } finally {
                isSaving = false
            }
        }
    }

    private fun confirmExisting(folder: String, existing: ExistingMultirepo, chosen: List<RepositoryCandidate>, onAdded: (String) -> Unit) {
        if (existing.known) {
            isSaving = false
            onAdded(existing.multirepo?.folder ?: folder)
            return
        }
        val accepted = chosen.filter { it.project == null && it.useProposedFolder }.map(::proposedFolder).toSet()
        scope.launch {
            try {
                onAdded(setup.register(folder, accepted).getOrThrow().multirepo.folder)
            } catch (failure: Exception) {
                error = failure
            } finally {
                isSaving = false
            }
        }
    }

    private fun addDeclared(alias: String, path: String) {
        val project = setup.projects().firstOrNull { sameFolder(it.config.baseRepositoryPath, path) }
        candidates = candidates + RepositoryCandidate(path, alias, project = project)
    }

    private fun addCandidate(path: String) {
        val projects = setup.projects()
        val project = projects.firstOrNull { sameFolder(it.config.baseRepositoryPath, path) }
        val taken = candidates.map { it.alias }.toSet()
        candidates = candidates + RepositoryCandidate(path, uniqueAlias(proposedAlias(File(path).name, name), taken), project = project)
    }

    private fun update(path: String, change: (RepositoryCandidate) -> RepositoryCandidate) {
        if (isSaving) return
        candidates = candidates.map { if (it.path == path) change(it) else it }
        detectGroups()
    }

    private fun detectGroups() {
        detection?.cancel()
        val members = included.filter { isValidMultirepoAlias(it.alias) }.map { candidate ->
            MultirepoMember(candidate.alias, candidate.project?.config ?: ProjectConfig(candidate.path, "", emptyList()))
        }
        if (members.size < 2) {
            groups = emptyList()
            return
        }
        detection = scope.launch {
            groups = runCatching { setup.detectGroups(members) }.getOrDefault(emptyList())
                .map { DetectedGroup(it.branch, it.aliases.distinct()) }
        }
    }
}

fun proposedAlias(repositoryName: String, multirepoName: String): String {
    val lower = repositoryName.lowercase()
    val stripped = lower.removePrefix("${multirepoName.lowercase()}-").ifEmpty { lower }
    return stripped.replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "repo" }
}

private fun multirepoPath(path: String): String = path.replace('\\', '/').trimEnd('/')

private fun uniqueAlias(alias: String, taken: Set<String>): String {
    if (alias !in taken) return alias
    return generateSequence(2) { it + 1 }.map { "$alias-$it" }.first { it !in taken }
}
