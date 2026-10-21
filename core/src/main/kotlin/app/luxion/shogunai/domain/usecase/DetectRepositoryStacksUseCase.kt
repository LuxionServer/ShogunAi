package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.DetectedStack
import app.luxion.shogunai.domain.model.Stack

class DetectRepositoryStacksUseCase(private val fileManager: FileManager) {

    operator fun invoke(repositoryPath: String): List<DetectedStack> {
        val root = repositoryPath.trimEnd('/', '\\')
        val folders = listOf("") + subfolders(root)
        val evidence = folders.flatMap { folder -> entriesOf(root, folder) }
        return listOfNotNull(
            stack(Stack.GRADLE, evidence.filter { it.isInRoot && it.name in GRADLE_FILES }),
            stack(Stack.ANDROID, evidence.filter { it.name in GRADLE_BUILD_FILES && declaresAndroid(root, it.relativePath) } + androidCatalog(root)),
            stack(Stack.XCODE, evidence.filter { it.name.endsWith(XCODE_PROJECT_SUFFIX) || (it.isInRoot && it.name == PACKAGE_SWIFT) }),
            stack(Stack.MAVEN, evidence.filter { it.isInRoot && it.name in MAVEN_FILES }),
            stack(Stack.DOCKER_COMPOSE, evidence.filter { it.isInRoot && it.name in COMPOSE_FILES }),
        )
    }

    private fun stack(stack: Stack, found: List<Entry>): DetectedStack? =
        found.map { it.relativePath }.distinct().takeIf { it.isNotEmpty() }?.let { DetectedStack(stack, it) }

    private fun subfolders(root: String): List<String> =
        fileManager.listNames(root).orEmpty().filter { name ->
            !name.startsWith(".") && name !in IGNORED_FOLDERS && fileManager.isDirectory("$root/$name")
        }

    private fun entriesOf(root: String, folder: String): List<Entry> {
        val directory = if (folder.isEmpty()) root else "$root/$folder"
        return fileManager.listNames(directory).orEmpty().map { Entry(folder, it) }
    }

    private fun declaresAndroid(root: String, relativePath: String): Boolean =
        fileManager.readText("$root/$relativePath")?.contains(ANDROID_PLUGIN_MARKER) == true

    private fun androidCatalog(root: String): List<Entry> =
        listOf(Entry("gradle", VERSION_CATALOG)).filter { declaresAndroid(root, it.relativePath) }

    private data class Entry(val folder: String, val name: String) {
        val isInRoot: Boolean get() = folder.isEmpty()
        val relativePath: String get() = if (folder.isEmpty()) name else "$folder/$name"
    }

    private companion object {
        val GRADLE_FILES = setOf("gradlew", "settings.gradle", "settings.gradle.kts")
        val GRADLE_BUILD_FILES = setOf("build.gradle", "build.gradle.kts")
        val MAVEN_FILES = setOf("pom.xml", "mvnw")
        val COMPOSE_FILES = setOf("compose.yaml", "compose.yml", "docker-compose.yaml", "docker-compose.yml")
        val IGNORED_FOLDERS = setOf("build", "node_modules", "Pods")
        const val XCODE_PROJECT_SUFFIX = ".xcodeproj"
        const val PACKAGE_SWIFT = "Package.swift"
        const val ANDROID_PLUGIN_MARKER = "com.android."
        const val VERSION_CATALOG = "libs.versions.toml"
    }
}
