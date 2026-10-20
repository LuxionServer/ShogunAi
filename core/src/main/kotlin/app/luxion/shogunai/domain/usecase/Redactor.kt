package app.luxion.shogunai.domain.usecase

class Redactor(
    private val home: String,
    private val userName: String,
    private val projectNames: () -> Collection<String>,
    private val branchNames: () -> Collection<String>,
) {
    fun redact(text: String): String =
        text.lineSequence().joinToString("\n") { line ->
            if (STACK_FRAME.containsMatchIn(line)) line else redactLine(line)
        }

    private fun redactLine(line: String): String {
        var result = line
        branchNames().filter { it.isNotBlank() }.sortedByDescending { it.length }.forEach { branch ->
            result = result.replace(branch, BRANCH)
        }
        projectNames().filter(::isDistinctiveName).sortedByDescending { it.length }.forEach { name ->
            result = result.replace(wholeWord(name), PROJECT)
        }
        result = PATH.replace(result, PATH_PLACEHOLDER)
        if (home.length > 1) result = result.replace(home, USER)
        if (userName.length >= MIN_NAME_LENGTH) result = result.replace(wholeWord(userName), USER)
        return result
    }

    private fun isDistinctiveName(name: String): Boolean =
        name.length >= MIN_NAME_LENGTH && name.lowercase() !in COMMON_NAMES

    private fun wholeWord(name: String): Regex = Regex("(?<![\\w-])${Regex.escape(name)}(?![\\w-])")

    private companion object {
        const val BRANCH = "<branch>"
        const val PROJECT = "<project>"
        const val PATH_PLACEHOLDER = "<path>"
        const val USER = "<user>"
        const val MIN_NAME_LENGTH = 3
        val STACK_FRAME = Regex("^\\s*(at |\\.\\.\\. \\d+ (more|common))")
        val PATH = Regex("(?<![\\w:/.<>~-])(?:~|[A-Za-z]:[\\\\/]|/)[^\\s'\"`,;()\\[\\]{}]*")
        val COMMON_NAMES = setOf(
            "app", "apps", "src", "main", "test", "tests", "core", "lib", "api", "web", "ios", "android",
            "build", "docs", "data", "common", "shared", "client", "server", "project", "repo", "demo",
        )
    }
}
