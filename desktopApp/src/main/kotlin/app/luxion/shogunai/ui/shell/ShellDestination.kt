package app.luxion.shogunai.ui.shell

sealed interface ShellDestination {
    data class Project(val projectId: String, val previousWorktreesRoot: String? = null) : ShellDestination
    data class ProjectSettings(val projectId: String) : ShellDestination
    data object AddProject : ShellDestination

    val isForm: Boolean get() = this is ProjectSettings || this is AddProject

    val referencedProjectId: String?
        get() = when (this) {
            is Project -> projectId
            is ProjectSettings -> projectId
            AddProject -> null
        }

    fun storageKey(): String? = when (this) {
        is Project -> PROJECT_PREFIX + projectId
        is ProjectSettings, AddProject -> null
    }

    companion object {
        private const val PROJECT_PREFIX = "project:"

        fun fromStorageKey(key: String?): ShellDestination? = when {
            key == null -> null
            key.startsWith(PROJECT_PREFIX) -> key.removePrefix(PROJECT_PREFIX).takeIf { it.isNotEmpty() }?.let(::Project)
            else -> null
        }
    }
}
