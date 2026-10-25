package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.ComposeProjectRegistry
import app.luxion.shogunai.domain.io.ComposeRegistryEntry

class FakeComposeProjectRegistry(
    initial: Map<String, List<ComposeRegistryEntry>> = emptyMap(),
    var failing: Boolean = false,
) : ComposeProjectRegistry {

    val recorded = initial.mapValues { it.value.toMutableList() }.toMutableMap()

    override fun entries(repositoryPath: String): List<ComposeRegistryEntry> = recorded[repositoryPath].orEmpty().toList()

    override fun record(repositoryPath: String, entry: ComposeRegistryEntry) {
        if (failing) throw IllegalStateException("registry not writable")
        val entries = recorded.getOrPut(repositoryPath) { mutableListOf() }
        entries.removeAll { it.project == entry.project }
        entries += entry
    }

    override fun drop(repositoryPath: String, projects: Set<String>) {
        if (failing) throw IllegalStateException("registry not writable")
        recorded[repositoryPath]?.removeAll { it.project in projects }
    }

    override fun recordedElsewhere(repositoryPath: String): Set<String> =
        recorded.filterKeys { it != repositoryPath }.values.flatten().map { it.project }.toSet()
}
