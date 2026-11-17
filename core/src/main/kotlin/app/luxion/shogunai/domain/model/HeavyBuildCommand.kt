package app.luxion.shogunai.domain.model

object HeavyBuildCommand {

    private val separators = Regex("[;&|()\\n`]")
    private val assignment = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*")
    private val launchers = setOf("env", "time", "nice", "nohup", "command", "exec")
    private val windowsSuffixes = listOf(".exe", ".cmd", ".bat")
    private val gradleExecutables = setOf("gradle", "gradlew")
    private val mavenExecutables = setOf("mvn", "mvnw")
    private val gradleLightOptions = setOf("--stop", "--status", "--version", "-v")
    private val gradleLightTasks = setOf("help", "tasks")
    private val mavenLightOptions = setOf("--version", "-v")
    private val xcodebuildLightOptions = setOf("-version", "-list", "-showsdks", "-showdestinations", "-showBuildSettings")
    private val swiftBuildCommands = setOf("build", "test")

    fun unwrappedBuilds(command: String): List<String> =
        command.split(separators).map { it.trim() }.filter { isUnwrappedBuild(words(it)) }

    private fun isUnwrappedBuild(words: List<String>): Boolean {
        val start = words.indexOfFirst { !assignment.matches(it) && executableName(it) !in launchers }
        if (start < 0) return false
        val arguments = words.drop(start + 1)
        return when (executableName(words[start])) {
            in gradleExecutables -> !isLightGradle(arguments)
            in mavenExecutables -> arguments.none { it in mavenLightOptions }
            "xcodebuild" -> arguments.none { it in xcodebuildLightOptions }
            "swift" -> arguments.firstOrNull { !it.startsWith("-") } in swiftBuildCommands
            else -> false
        }
    }

    private fun isLightGradle(arguments: List<String>): Boolean {
        if (arguments.any { it in gradleLightOptions }) return true
        val tasks = arguments.filterNot { it.startsWith("-") }
        return tasks.isNotEmpty() && tasks.all { it in gradleLightTasks }
    }

    private fun executableName(word: String): String {
        val name = word.substringAfterLast('/').substringAfterLast('\\').lowercase()
        val suffix = windowsSuffixes.firstOrNull { name.endsWith(it) }
        return if (suffix == null) name else name.removeSuffix(suffix)
    }

    private fun words(segment: String): List<String> =
        segment.split(Regex("\\s+")).map { it.replace("\"", "").replace("'", "") }.filter { it.isNotEmpty() }
}
