package app.luxion.shogunai.domain.model

object OrcaComputerUse {

    private val executables = setOf("orca", "orca-ide")
    private val windowsSuffixes = listOf(".exe", ".cmd")
    private val readOnlyCommands = setOf("capabilities", "permissions", "list-apps", "list-windows", "get-app-state")
    private const val RESTORE_WINDOW = "--restore-window"
    private val separators = Regex("[;&|()\\n`]")

    fun drivesDesktop(command: String): Boolean =
        command.split(separators).any { segment -> segmentDrivesDesktop(words(segment)) }

    private fun segmentDrivesDesktop(words: List<String>): Boolean {
        val start = words.indices.firstOrNull { isOrca(words[it]) && words.getOrNull(it + 1) == "computer" } ?: return false
        val arguments = words.drop(start + 2)
        val subcommand = arguments.firstOrNull { !it.startsWith("-") } ?: return false
        return when (subcommand) {
            "get-app-state" -> RESTORE_WINDOW in arguments
            in readOnlyCommands -> false
            else -> true
        }
    }

    private fun isOrca(word: String): Boolean {
        val name = word.substringAfterLast('/').substringAfterLast('\\').lowercase()
        val bare = windowsSuffixes.firstOrNull { name.endsWith(it) }?.let { name.removeSuffix(it) } ?: name
        return bare in executables
    }

    private fun words(segment: String): List<String> =
        segment.trim().split(Regex("\\s+")).map { it.replace("\"", "").replace("'", "") }.filter { it.isNotEmpty() }
}
