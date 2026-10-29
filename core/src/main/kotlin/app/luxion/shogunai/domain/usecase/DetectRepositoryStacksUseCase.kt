package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.DetectedStack
import app.luxion.shogunai.domain.model.Stack

class DetectRepositoryStacksUseCase(private val fileManager: FileManager) {

    operator fun invoke(repositoryPath: String): List<DetectedStack> {
        val root = repositoryPath.trimEnd('/', '\\')
        val folders = listOf("") + repositorySubfolders(fileManager, root)
        val evidence = folders.flatMap { folder -> entriesOf(root, folder) }
        return listOfNotNull(
            stack(Stack.FLUTTER, evidence.filter { it.isInRoot && it.name == PUBSPEC && declaresFlutter(root, it.relativePath) }),
            stack(Stack.GRADLE, gradleEvidence(evidence)),
            stack(Stack.ANDROID, evidence.filter { it.name in ANDROID_DECLARING_FILES && declaresAndroid(root, it.relativePath) } + androidCatalog(root)),
            stack(Stack.XCODE, evidence.filter { it.name.endsWith(XCODE_PROJECT_SUFFIX) || (it.isInRoot && it.name == PACKAGE_SWIFT) }),
            stack(Stack.MAVEN, evidence.filter { it.isInRoot && it.name in MAVEN_FILES }),
            stack(Stack.DOCKER_COMPOSE, evidence.filter { it.isInRoot && it.name in COMPOSE_FILES }),
        )
    }

    private fun stack(stack: Stack, found: List<Entry>): DetectedStack? =
        found.map { it.relativePath }.distinct().takeIf { it.isNotEmpty() }?.let { DetectedStack(stack, it) }

    private fun gradleEvidence(evidence: List<Entry>): List<Entry> =
        evidence.filter { it.isInRoot && it.name in GRADLE_ROOT_FILES }
            .ifEmpty { evidence.filter { !it.isInRoot && it.name in GRADLE_SETTINGS_FILES } }

    private fun entriesOf(root: String, folder: String): List<Entry> {
        val directory = if (folder.isEmpty()) root else "$root/$folder"
        return fileManager.listNames(directory).orEmpty().map { Entry(folder, it) }
    }

    private fun declaresAndroid(root: String, relativePath: String): Boolean =
        fileManager.readText("$root/$relativePath")?.contains(ANDROID_PLUGIN_MARKER) == true

    private fun declaresFlutter(root: String, relativePath: String): Boolean =
        fileManager.readText("$root/$relativePath")?.contains(FLUTTER_SDK_MARKER) == true

    private fun androidCatalog(root: String): List<Entry> =
        listOf(Entry("gradle", VERSION_CATALOG)).filter { declaresAndroid(root, it.relativePath) }

    private data class Entry(val folder: String, val name: String) {
        val isInRoot: Boolean get() = folder.isEmpty()
        val relativePath: String get() = if (folder.isEmpty()) name else "$folder/$name"
    }

    private companion object {
        private val ANDROID_DECLARING_FILES = setOf("build.gradle", "build.gradle.kts") + GRADLE_SETTINGS_FILES
        private val MAVEN_FILES = setOf("pom.xml", "mvnw")
        private val COMPOSE_FILES = setOf("compose.yaml", "compose.yml", "docker-compose.yaml", "docker-compose.yml")
        const val XCODE_PROJECT_SUFFIX = ".xcodeproj"
        const val PACKAGE_SWIFT = "Package.swift"
        const val ANDROID_PLUGIN_MARKER = "com.android."
        const val VERSION_CATALOG = "libs.versions.toml"
        const val PUBSPEC = "pubspec.yaml"
        const val FLUTTER_SDK_MARKER = "sdk: flutter"
    }
}
