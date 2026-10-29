package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager

val GRADLE_SETTINGS_FILES = listOf("settings.gradle", "settings.gradle.kts")
val GRADLE_ROOT_FILES = listOf("gradlew") + GRADLE_SETTINGS_FILES
val IGNORED_REPOSITORY_FOLDERS = setOf("build", "node_modules", "Pods")

fun gradleBuildFolders(fileManager: FileManager, repositoryPath: String): List<String> {
    val root = repositoryPath.trimEnd('/', '\\')
    if (GRADLE_ROOT_FILES.any { fileManager.exists("$root/$it") }) return listOf("")
    return repositorySubfolders(fileManager, root).filter { folder ->
        GRADLE_SETTINGS_FILES.any { fileManager.exists("$root/$folder/$it") }
    }
}

fun nestedGradleBuildFolders(fileManager: FileManager, repositoryPath: String): List<String> =
    gradleBuildFolders(fileManager, repositoryPath).filter { it.isNotEmpty() }

fun nestedGradleBuildPaths(fileManager: FileManager, repositoryPath: String): List<String> {
    val root = repositoryPath.trimEnd('/', '\\')
    return nestedGradleBuildFolders(fileManager, root).map { "$root/$it" }
}

internal fun repositorySubfolders(fileManager: FileManager, root: String): List<String> =
    fileManager.listNames(root).orEmpty().filter { name ->
        !name.startsWith(".") && name !in IGNORED_REPOSITORY_FOLDERS && fileManager.isDirectory("$root/$name")
    }.sorted()
