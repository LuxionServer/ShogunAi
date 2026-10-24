package app.luxion.shogunai.domain.io

data class ComposeRegistryEntry(val project: String, val worktreePath: String)

interface ComposeProjectRegistry {

    fun entries(repositoryPath: String): List<ComposeRegistryEntry>

    fun record(repositoryPath: String, entry: ComposeRegistryEntry)

    fun drop(repositoryPath: String, projects: Set<String>)

    fun recordedElsewhere(repositoryPath: String): Set<String>
}
