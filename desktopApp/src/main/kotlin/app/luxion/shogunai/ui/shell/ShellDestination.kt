package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.ui.machine.MachineResource

sealed interface ShellDestination {
    data object Active : ShellDestination
    data class All(val projectFilter: String? = null, val previousWorktreesRoot: String? = null) : ShellDestination
    data class ProjectSettings(val projectId: String) : ShellDestination
    data class AddProject(val folder: String? = null) : ShellDestination
    data class Machine(val resource: MachineResource) : ShellDestination

    val isForm: Boolean get() = this is ProjectSettings || this is AddProject

    val referencedProjectId: String?
        get() = when (this) {
            Active -> null
            is All -> projectFilter
            is ProjectSettings -> projectId
            is AddProject -> null
            is Machine -> null
        }

    fun storageKey(): String? = when (this) {
        Active -> ACTIVE_KEY
        is All -> projectFilter?.let { PROJECT_PREFIX + it } ?: ALL_KEY
        is Machine -> MACHINE_PREFIX + resource.name
        is ProjectSettings, is AddProject -> null
    }

    companion object {
        private const val ACTIVE_KEY = "active"
        private const val ALL_KEY = "all"
        private const val PROJECT_PREFIX = "project:"
        private const val MACHINE_PREFIX = "machine:"

        fun fromStorageKey(key: String?): ShellDestination? = when {
            key == ACTIVE_KEY -> Active
            key == ALL_KEY -> All()
            key?.startsWith(PROJECT_PREFIX) == true ->
                key.removePrefix(PROJECT_PREFIX).takeIf { it.isNotEmpty() }?.let { All(projectFilter = it) }
            key?.startsWith(MACHINE_PREFIX) == true ->
                MachineResource.entries.firstOrNull { it.name == key.removePrefix(MACHINE_PREFIX) }?.let(::Machine)
            else -> null
        }
    }
}
