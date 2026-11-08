package app.luxion.shogunai.domain.usecase

object ToolVersions {
    const val MINIMUM_COMPOSE = "2.0"

    private val GRADLE_DISTRIBUTION = Regex("gradle-([^/]+?)-(?:bin|all)\\.zip$")
    private val MAVEN_DISTRIBUTION = Regex("apache-maven-([^/]+?)-bin\\.zip$")
    private val GRADLE_PRINTED = Regex("(?m)^Gradle (\\S+)")
    private val MAVEN_PRINTED = Regex("Apache Maven (\\S+)")
    private val JAVA_PRINTED = Regex("version \"(\\d+)(?:\\.(\\d+))?")
    private val COMPOSE_PRINTED = Regex("version v?(\\d+\\.\\d+(?:\\.\\d+)?)")
    private val NUMERIC = Regex("^\\d+(?:\\.\\d+)*")

    fun gradleDistribution(url: String?): String? = url?.let { GRADLE_DISTRIBUTION.firstGroup(it) }

    fun mavenDistribution(url: String?): String? = url?.let { MAVEN_DISTRIBUTION.firstGroup(it) }

    fun gradlePrinted(output: String): String? = GRADLE_PRINTED.firstGroup(output)

    fun mavenPrinted(output: String): String? = MAVEN_PRINTED.firstGroup(output)

    fun composePrinted(output: String): String? = COMPOSE_PRINTED.firstGroup(output)

    fun javaMajor(output: String): Int? {
        val match = JAVA_PRINTED.find(output) ?: return null
        val (first, second) = match.destructured
        return if (first == "1" && second.isNotEmpty()) second.toInt() else first.toInt()
    }

    fun numeric(version: String): String? = NUMERIC.find(version)?.value

    private fun Regex.firstGroup(text: String): String? = find(text)?.let { it.groupValues[1] }
}

object JavaCompatibility {
    private val FIRST_GRADLE_FOR_JAVA = mapOf(
        9 to "4.3",
        10 to "4.7",
        11 to "5.0",
        12 to "5.4",
        13 to "6.0",
        14 to "6.3",
        15 to "6.7",
        16 to "7.0",
        17 to "7.3",
        18 to "7.5",
        19 to "7.6",
        20 to "8.3",
        21 to "8.5",
        22 to "8.8",
        23 to "8.10",
        24 to "8.14",
        25 to "9.1",
    )

    fun minimumJavaForGradle(gradle: String): Int = if (CheckInstallationUseCase.isAtLeast(gradle, "9")) 17 else 8

    fun minimumJavaForMaven(maven: String): Int = when {
        CheckInstallationUseCase.isAtLeast(maven, "4") -> 17
        CheckInstallationUseCase.isAtLeast(maven, "3.9") -> 8
        else -> 7
    }

    fun firstGradleFor(java: Int): String? = FIRST_GRADLE_FOR_JAVA[java]
}
