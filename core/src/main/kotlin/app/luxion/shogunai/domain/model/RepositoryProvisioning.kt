package app.luxion.shogunai.domain.model

data class RepositoryProvisioning(
    val copyPatterns: List<String> = emptyList(),
    val worktreeIncludePatterns: List<String> = emptyList(),
    val providers: RepositoryProviders = RepositoryProviders(),
    val environment: EnvironmentDeclaration = EnvironmentDeclaration(),
) {
    val allPatterns: List<String> get() = (copyPatterns + worktreeIncludePatterns).distinct()

    val isEmpty: Boolean get() = allPatterns.isEmpty()

    companion object {
        const val CONFIG_FILE_NAME = ".shogunai.json"
        const val WORKTREE_INCLUDE_FILE_NAME = ".worktreeinclude"
    }
}

data class RepositoryProviders(
    val gradle: GradleProvider? = null,
)

data object GradleProvider
