package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.HostOs

data class InstalledJava(val home: String, val major: Int)

data class JavaSearch(val found: List<InstalledJava>, val searched: List<String>) {
    fun find(major: Int): InstalledJava? = found.firstOrNull { it.major == major }
}

class JavaInstallations(
    private val fileManager: FileManager,
    private val environmentVariable: (String) -> String?,
    private val os: HostOs,
    private val home: String,
) {

    fun search(settings: GradleJavaSettings, gradleUserHome: String, client: InstalledJava?): JavaSearch {
        val homes = settings.installationPaths + if (settings.autoDetect) listOfNotNull(environmentVariable(JAVA_HOME)?.takeIf { it.isNotBlank() }) else emptyList()
        val parents = if (settings.autoDetect) parents(gradleUserHome) else emptyList()
        val found = homes.flatMap(::javaAt) + parents.flatMap(::javasUnder) + listOfNotNull(client)
        return JavaSearch(found, homes + parents)
    }

    private fun parents(gradleUserHome: String): List<String> = listOf(
        path(gradleUserHome, "jdks"),
        path(home, ".jdks"),
        path(home, ".sdkman", "candidates", "java"),
        path(home, ".asdf", "installs", "java"),
    ) + when (os) {
        HostOs.MACOS -> listOf("/Library/Java/JavaVirtualMachines", path(home, "Library", "Java", "JavaVirtualMachines"))
        HostOs.WINDOWS -> listOf("C:\\Program Files\\Java", "C:\\Program Files\\Eclipse Adoptium")
        HostOs.LINUX -> listOf("/usr/lib/jvm")
    }

    private fun javasUnder(parent: String): List<InstalledJava> = children(parent).flatMap { child ->
        javaAt(child).ifEmpty { children(child).flatMap(::javaAt) }
    }

    private fun javaAt(folder: String): List<InstalledJava> =
        listOf(folder, path(folder, "Contents", "Home")).firstNotNullOfOrNull { candidate ->
            fileManager.readText(path(candidate, RELEASE))?.let(ToolVersions::javaReleaseMajor)?.let { InstalledJava(candidate, it) }
        }.let(::listOfNotNull)

    private fun children(folder: String): List<String> = fileManager.listNames(folder).orEmpty().map { path(folder, it) }

    private fun path(parent: String, vararg parts: String): String {
        val separator = if (os.isWindows) "\\" else "/"
        return (listOf(parent.trimEnd('/', '\\')) + parts).joinToString(separator)
    }

    private companion object {
        const val JAVA_HOME = "JAVA_HOME"
        const val RELEASE = "release"
    }
}
