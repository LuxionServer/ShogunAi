package app.luxion.shogunai.domain.model

enum class NodePackageManager(val id: String, val lockfiles: List<String>, val markers: List<String>) {
    PNPM("pnpm", listOf("pnpm-lock.yaml"), listOf(".modules.yaml")),
    YARN("yarn", listOf("yarn.lock"), listOf(".yarn-integrity", ".yarn-state.yml")),
    BUN("bun", listOf("bun.lock", "bun.lockb"), emptyList()),
    NPM("npm", listOf("package-lock.json", "npm-shrinkwrap.json"), listOf(".package-lock.json")),
}

val NODE_LOCKFILES: List<String> = NodePackageManager.entries.flatMap { it.lockfiles }

const val PACKAGE_JSON = "package.json"

const val NODE_MODULES = "node_modules"

data class NodeProject(val manager: NodePackageManager, val lockfile: String, val yarnBerry: Boolean = false) {
    val installCommand: List<String>
        get() = when (manager) {
            NodePackageManager.NPM -> listOf("npm", "ci")
            NodePackageManager.PNPM -> listOf("pnpm", "install", "--frozen-lockfile")
            NodePackageManager.YARN -> listOf("yarn", "install", if (yarnBerry) "--immutable" else "--frozen-lockfile")
            NodePackageManager.BUN -> listOf("bun", "install", "--frozen-lockfile")
        }
}

fun chooseNodeProject(lockfiles: Set<String>, packageManager: String?, yarnBerry: Boolean): NodeProject? {
    val candidates = NodePackageManager.entries.filter { manager -> manager.lockfiles.any { it in lockfiles } }
    val manager = candidates.firstOrNull { it.id == packageManager } ?: candidates.firstOrNull() ?: return null
    return NodeProject(manager, manager.lockfiles.first { it in lockfiles }, yarnBerry && manager == NodePackageManager.YARN)
}

fun packageManagerName(packageJson: String?): String? {
    val match = packageJson?.let(PACKAGE_MANAGER_FIELD::find) ?: return null
    return match.groupValues[1]
}

private val PACKAGE_MANAGER_FIELD = Regex("\"packageManager\"\\s*:\\s*\"([A-Za-z]+)@")

sealed interface NodeDependencies {
    data class Cloned(val manager: NodePackageManager, val folders: List<String>) : NodeDependencies

    data class Installed(val manager: NodePackageManager) : NodeDependencies

    data object Present : NodeDependencies

    data class Needed(val project: NodeProject?, val reason: NodeDependenciesFailure) : NodeDependencies
}

sealed interface NodeDependenciesFailure {
    data object NoLockfile : NodeDependenciesFailure

    data object NotApproved : NodeDependenciesFailure

    data class NotFound(val executable: String) : NodeDependenciesFailure

    data class Exited(val exitCode: Int, val errorLines: List<String>) : NodeDependenciesFailure

    data class Unexpected(val message: String) : NodeDependenciesFailure
}

fun nodeRuleText(project: NodeProject?): String {
    val (install, lockfile) = if (project == null) {
        "the package manager's install" to "the lockfile"
    } else {
        "`${project.manager.id} install`" to "`${project.lockfile}`"
    }
    return "This worktree has its own node_modules: run $install after `package.json` or $lockfile change, " +
        "and never link or copy node_modules from another worktree. " +
        "Give dev servers the port of a variable declared with `\${port}` in .shogunai.json's env instead of a fixed port."
}
