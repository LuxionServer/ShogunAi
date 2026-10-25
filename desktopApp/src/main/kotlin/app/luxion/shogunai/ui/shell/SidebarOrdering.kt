package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.model.SidebarOrder

sealed interface SidebarLevel {
    data object TopLevel : SidebarLevel
    data class Folder(val folder: String) : SidebarLevel
}

fun projectSidebarKey(projectId: String): String = "project:$projectId"

fun multirepoSidebarKey(folder: String): String = "multirepo:$folder"

fun <T> List<T>.orderedBy(saved: List<String>, key: (T) -> String, name: (T) -> String): List<T> {
    val byKey = associateBy(key)
    val placed = saved.distinct().mapNotNull { byKey[it] }
    val placedKeys = placed.mapTo(mutableSetOf(), key)
    return placed + filterNot { key(it) in placedKeys }.sortedBy { name(it).lowercase() }
}

fun SidebarOrder.savedFor(level: SidebarLevel): List<String> = when (level) {
    SidebarLevel.TopLevel -> topLevel
    is SidebarLevel.Folder -> multirepos[level.folder].orEmpty()
}

fun SidebarOrder.with(level: SidebarLevel, keys: List<String>?): SidebarOrder = when (level) {
    SidebarLevel.TopLevel -> copy(topLevel = keys.orEmpty())
    is SidebarLevel.Folder -> copy(multirepos = if (keys == null) multirepos - level.folder else multirepos + (level.folder to keys))
}

fun List<String>.moving(key: String, toIndex: Int): List<String> {
    if (key !in this) return this
    val rest = filterNot { it == key }.toMutableList()
    rest.add(toIndex.coerceIn(0, rest.size), key)
    return rest
}
