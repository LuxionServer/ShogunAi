package app.luxion.shogunai.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun interface XcodeWorkspaceFinder {
    suspend fun find(worktree: String): String
}

class FileSystemXcodeWorkspaceFinder(private val maxDepth: Int = MAX_DEPTH) : XcodeWorkspaceFinder {

    override suspend fun find(worktree: String): String = withContext(Dispatchers.IO) { search(File(worktree)) }

    private fun search(root: File): String {
        val workspaces = mutableListOf<Candidate>()
        val projects = mutableListOf<Candidate>()
        var level = listOf(root)
        for (depth in 1..maxDepth) {
            val next = mutableListOf<File>()
            for (child in level.flatMap { it.listFiles().orEmpty().asList() }.filter { it.isDirectory }) {
                when {
                    child.name.endsWith(WORKSPACE_SUFFIX) -> workspaces += Candidate(depth, child)
                    child.name.endsWith(PROJECT_SUFFIX) -> projects += Candidate(depth, child)
                    child.name !in SKIPPED && !child.name.startsWith(".") -> next += child
                }
            }
            level = next
        }
        val best = (workspaces.ifEmpty { projects }).minWithOrNull(compareBy({ it.depth }, { it.folder.path }))
        return best?.folder?.path ?: root.path
    }

    private class Candidate(val depth: Int, val folder: File)

    private companion object {
        const val MAX_DEPTH = 4
        const val WORKSPACE_SUFFIX = ".xcworkspace"
        const val PROJECT_SUFFIX = ".xcodeproj"
        val SKIPPED = setOf("Pods", "build", "DerivedData", "node_modules", "Carthage")
    }
}
