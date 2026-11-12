package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.joinPath

data class GradleJavaSettings(
    val javaHome: GradleJavaHome? = null,
    val installationPaths: List<String> = emptyList(),
    val autoDetect: Boolean = true,
    val autoDownload: Boolean = true,
    val daemonCriteria: DaemonJvmCriteria? = null,
    val toolchains: List<Int> = emptyList(),
    val toolchainResolver: Boolean = false,
    val androidGradlePlugin: String? = null,
)

data class GradleJavaHome(val value: String, val propertiesFile: String)

data class DaemonJvmCriteria(val path: String, val version: Int?, val downloadable: Boolean)

class GradleJavaSettingsReader(
    private val fileManager: FileManager,
    private val environmentVariable: (String) -> String?,
    private val home: String,
) {

    val gradleUserHome: String
        get() = environmentVariable(GRADLE_USER_HOME)?.takeIf { it.isNotBlank() } ?: joinPath(home, ".gradle")

    fun read(repository: String): GradleJavaSettings {
        val userProperties = joinPath(gradleUserHome, GRADLE_PROPERTIES)
        val repositoryProperties = joinPath(repository, GRADLE_PROPERTIES)
        val user = fileManager.readProperties(userProperties)
        val project = fileManager.readProperties(repositoryProperties)
        val properties = project + user
        val javaHome = user[JAVA_HOME_PROPERTY]?.let { GradleJavaHome(it, userProperties) }
            ?: project[JAVA_HOME_PROPERTY]?.let { GradleJavaHome(it, repositoryProperties) }
        val settingsScripts = SETTINGS_SCRIPTS.mapNotNull { fileManager.readText(joinPath(repository, it)) }
        val buildScripts = buildScripts(repository)
        return GradleJavaSettings(
            javaHome = javaHome?.takeIf { it.value.isNotBlank() },
            installationPaths = properties[INSTALLATION_PATHS].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty),
            autoDetect = properties[AUTO_DETECT]?.trim() != "false",
            autoDownload = properties[AUTO_DOWNLOAD]?.trim() != "false",
            daemonCriteria = daemonCriteria(repository),
            toolchains = buildScripts.flatMap { script -> TOOLCHAINS.flatMap { it.findAll(script).map { match -> match.groupValues[1].toInt() } } }
                .distinct().sorted(),
            toolchainResolver = settingsScripts.any { TOOLCHAIN_RESOLVER in it },
            androidGradlePlugin = catalogAndroidGradlePlugin(repository) ?: buildScripts.firstNotNullOfOrNull(::scriptAndroidGradlePlugin),
        )
    }

    private fun daemonCriteria(repository: String): DaemonJvmCriteria? {
        val path = joinPath(repository, DAEMON_JVM_PROPERTIES)
        if (!fileManager.exists(path)) return null
        val properties = fileManager.readProperties(path)
        return DaemonJvmCriteria(
            path,
            properties[TOOLCHAIN_VERSION]?.let { it.trim().toIntOrNull() },
            properties.keys.any { it.startsWith(TOOLCHAIN_URL_PREFIX) },
        )
    }

    private fun buildScripts(repository: String): List<String> {
        val rootScripts = (BUILD_SCRIPTS + SETTINGS_SCRIPTS).map { joinPath(repository, it) }
        val children = fileManager.listNames(repository).orEmpty().filterNot { it.startsWith('.') || it in CONVENTION_FOLDERS }
        val childScripts = children.flatMap { child -> BUILD_SCRIPTS.map { joinPath(repository, child, it) } }
        val conventionScripts = CONVENTION_FOLDERS.flatMap { scriptsUnder(joinPath(repository, it), CONVENTION_DEPTH) }
        return (rootScripts + childScripts + conventionScripts).mapNotNull(fileManager::readText)
    }

    private fun scriptsUnder(folder: String, depth: Int): List<String> {
        val names = fileManager.listNames(folder) ?: return emptyList()
        return names.filterNot { it.startsWith('.') || it == BUILD_FOLDER }.flatMap { name ->
            val path = joinPath(folder, name)
            when {
                SCRIPT_EXTENSIONS.any(name::endsWith) -> listOf(path)
                depth > 0 -> scriptsUnder(path, depth - 1)
                else -> emptyList()
            }
        }
    }

    private fun catalogAndroidGradlePlugin(repository: String): String? {
        val catalog = fileManager.readText(joinPath(repository, VERSION_CATALOG)) ?: return null
        val versions = mutableMapOf<String, String>()
        var section = ""
        val entries = mutableListOf<String>()
        catalog.lineSequence().map(String::trim).forEach { line ->
            val header = SECTION.matchEntire(line)
            when {
                header != null -> section = header.groupValues[1]
                section == VERSIONS_SECTION -> CATALOG_VERSION.matchEntire(line)?.let { versions[it.groupValues[1]] = it.groupValues[2] }
                AGP_ENTRY.containsMatchIn(line) -> entries += line
            }
        }
        return entries.firstNotNullOfOrNull { entry ->
            CATALOG_VERSION_REF.find(entry)?.let { versions[it.groupValues[1]] }
                ?: CATALOG_INLINE_VERSION.firstGroup(entry)
                ?: AGP_COORDINATES.firstGroup(entry)
        }
    }

    private fun scriptAndroidGradlePlugin(script: String): String? = AGP_PLUGIN.firstGroup(script) ?: AGP_COORDINATES.firstGroup(script)

    private fun Regex.firstGroup(text: String): String? = find(text)?.let { it.groupValues[1] }

    private companion object {
        private const val GRADLE_USER_HOME = "GRADLE_USER_HOME"
        private const val GRADLE_PROPERTIES = "gradle.properties"
        private const val JAVA_HOME_PROPERTY = "org.gradle.java.home"
        private const val INSTALLATION_PATHS = "org.gradle.java.installations.paths"
        private const val AUTO_DETECT = "org.gradle.java.installations.auto-detect"
        private const val AUTO_DOWNLOAD = "org.gradle.java.installations.auto-download"
        private const val DAEMON_JVM_PROPERTIES = "gradle/gradle-daemon-jvm.properties"
        private const val TOOLCHAIN_VERSION = "toolchainVersion"
        private const val TOOLCHAIN_URL_PREFIX = "toolchainUrl."
        private const val TOOLCHAIN_RESOLVER = "org.gradle.toolchains.foojay-resolver"
        private const val VERSION_CATALOG = "gradle/libs.versions.toml"
        private const val VERSIONS_SECTION = "versions"
        private const val BUILD_FOLDER = "build"
        private const val CONVENTION_DEPTH = 8
        private val BUILD_SCRIPTS = listOf("build.gradle", "build.gradle.kts")
        private val SETTINGS_SCRIPTS = listOf("settings.gradle", "settings.gradle.kts")
        private val CONVENTION_FOLDERS = setOf("buildSrc", "build-logic")
        private val SCRIPT_EXTENSIONS = listOf(".gradle", ".kts", ".kt")
        private val TOOLCHAINS = listOf(Regex("jvmToolchain\\(\\s*(\\d+)\\s*\\)"), Regex("JavaLanguageVersion\\.of\\(\\s*(\\d+)\\s*\\)"))
        private val SECTION = Regex("\\[([\\w-]+)]")
        private val CATALOG_VERSION = Regex("([\\w.-]+)\\s*=\\s*\"([^\"]+)\"")
        private val CATALOG_VERSION_REF = Regex("version\\.ref\\s*=\\s*\"([^\"]+)\"")
        private val CATALOG_INLINE_VERSION = Regex("version\\s*=\\s*\"([^\"]+)\"")
        private val AGP_ENTRY = Regex(
            "\"com\\.android\\.(?:application|library)\"|com\\.android\\.tools\\.build:gradle[:\"]|\"com\\.android\\.tools\\.build\"\\s*,\\s*name\\s*=\\s*\"gradle\"",
        )
        private val AGP_COORDINATES = Regex("com\\.android\\.tools\\.build:gradle:(\\d[^\"'\\s)]*)")
        private val AGP_PLUGIN = Regex("id\\s*\\(?\\s*[\"']com\\.android\\.(?:application|library)[\"']\\s*\\)?\\s*version\\s*[\"']([^\"']+)[\"']")
    }
}
