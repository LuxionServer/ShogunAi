package app.luxion.shogunai.ui.multirepo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.Project

class MultirepoCatalog(private val known: () -> List<Multirepo>) {
    var multirepos by mutableStateOf<List<Multirepo>>(emptyList())
        private set

    fun refresh() {
        val loaded = runCatching(known).getOrDefault(emptyList()).sortedBy { it.name.lowercase() }
        if (loaded != multirepos) multirepos = loaded
    }

    fun find(folder: String): Multirepo? = multirepos.firstOrNull { it.folder == folder }
}

data class MultirepoMembership(val multirepo: Multirepo, val aliases: Map<String, String>) {
    val folder: String get() = multirepo.folder
    val name: String get() = multirepo.name

    fun aliasOf(projectId: String): String? = aliases[projectId]

    fun projectIdOf(alias: String): String? = aliases.entries.firstOrNull { it.value == alias }?.key
}

fun multirepoMemberships(multirepos: List<Multirepo>, projects: List<Project>): List<MultirepoMembership> =
    multirepos.map { multirepo ->
        MultirepoMembership(
            multirepo,
            if (!multirepo.approved) {
                emptyMap()
            } else {
                projects.mapNotNull { project -> multirepo.aliasOf(project.config.baseRepositoryPath)?.let { project.id to it } }.toMap()
            },
        )
    }
