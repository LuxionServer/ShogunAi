package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager

class CheckGradleBuildCacheUseCase(
    private val fileManager: FileManager,
    private val gradleUserHome: String,
) {
    operator fun invoke(repositoryPath: String): Boolean =
        (listOf(repositoryPath) + nestedGradleBuildPaths(fileManager, repositoryPath) + gradleUserHome).any { directory ->
            fileManager.readProperties("${directory.trimEnd('/', '\\')}/$PROPERTIES_FILE")[CACHING_PROPERTY]
                ?.trim()
                .equals("true", ignoreCase = true)
        }

    private companion object {
        const val PROPERTIES_FILE = "gradle.properties"
        const val CACHING_PROPERTY = "org.gradle.caching"
    }
}
