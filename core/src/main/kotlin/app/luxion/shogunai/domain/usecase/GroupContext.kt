package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.joinPath

class GroupContext(private val fileManager: FileManager) {

    fun groupLines(multirepo: Multirepo, group: WorktreeGroup, worktreePath: String): List<String> {
        val own = group.worktrees.firstOrNull { sameDirectory(it.worktree.path, worktreePath) } ?: return emptyList()
        val others = group.worktrees.filterNot { it === own }
        val missing = multirepo.aliases.filterNot { it in group.aliases }
        return listOfNotNull(
            "ShogunAi multirepo ${multirepo.name}: this worktree is ${own.alias} in the group ${group.branch}.",
            others.takeIf { it.isNotEmpty() }?.let { list ->
                "Other repositories of the group: " + list.joinToString("; ") { "${it.alias} at ${it.worktree.path}" } + "."
            },
            missing.takeIf { it.isNotEmpty() }?.let { "Not in the group: ${it.joinToString()}; ${addHint(multirepo, group, it.first())}." },
            openSpecLine(multirepo, group),
        )
    }

    fun multirepoLines(multirepo: Multirepo): List<String> = listOfNotNull(
        "ShogunAi multirepo ${multirepo.name} at ${multirepo.folder}, with the repositories " +
            multirepo.repositories.joinToString { "${it.alias} (${it.path})" }.ifEmpty { "none yet" } + ".",
        joinPath(multirepo.folder, AGENTS_FILE).takeIf(fileManager::exists)?.let { "Read $it for what all the repositories share." },
        "Run `shogun status` to list the groups, `shogun new <id>` to create one" +
            (multirepo.declaration.defaultRepos.takeIf { it.isNotEmpty() }?.let { " in ${it.joinToString()}" } ?: " with --repos") +
            " and `shogun open <id> --herdr` to open its worktrees, each with its own agent.",
    )

    private fun addHint(multirepo: Multirepo, group: WorktreeGroup, alias: String): String = when {
        group.branch.startsWith("feature/") -> "add one with `shogun new ${group.title} --repos $alias` from ${multirepo.folder}"
        group.branch.startsWith("fix/") -> "add one with `shogun new ${group.title} --fix --repos $alias` from ${multirepo.folder}"
        else -> "add one by creating a worktree on ${group.branch} in it"
    }

    private fun openSpecLine(multirepo: Multirepo, group: WorktreeGroup): String? {
        val paths = multirepo.repositories.mapNotNull { repository ->
            val folder = group.worktrees.firstOrNull { it.alias == repository.alias }?.worktree?.path ?: repository.path
            if (fileManager.exists(joinPath(folder, OPENSPEC_FOLDER))) joinPath(folder, AGENTS_FILE) else null
        }
        if (paths.isEmpty()) return null
        return "OpenSpec changes are planned in " + paths.joinToString(" and ") + "; read it before planning."
    }

    private companion object {
        const val AGENTS_FILE = "AGENTS.md"
        const val OPENSPEC_FOLDER = "openspec"
    }
}
