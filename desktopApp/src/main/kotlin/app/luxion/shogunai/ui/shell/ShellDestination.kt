package app.luxion.shogunai.ui.shell

sealed interface ShellDestination {
    data class All(val projectFilter: String? = null, val previousWorktreesRoot: String? = null) : ShellDestination
    data class ProjectSettings(val projectId: String) : ShellDestination
    data object AddProject : ShellDestination

    val isForm: Boolean get() = this is ProjectSettings || this is AddProject

    val referencedProjectId: String?
        get() = when (this) {
            is All -> projectFilter
            is ProjectSettings -> projectId
            AddProject -> null
        }

    fun storageKey(): String? = when (this) {
        is All -> projectFilter?.let { PROJECT_PREFIX + it } ?: ALL_KEY
        is ProjectSettings, AddProject -> null
    }

    companion object {
        private const val ALL_KEY = "all"
        private const val PROJECT_PREFIX = "project:"

        fun fromStorageKey(key: String?): ShellDestination? = when {
            key == ALL_KEY -> All()
            key?.startsWith(PROJECT_PREFIX) == true ->
                key.removePrefix(PROJECT_PREFIX).takeIf { it.isNotEmpty() }?.let { All(projectFilter = it) }
            else -> null
        }
    }
}
