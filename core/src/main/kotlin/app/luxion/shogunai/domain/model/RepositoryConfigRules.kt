package app.luxion.shogunai.domain.model

import java.nio.file.Path

sealed interface RepositoryConfigIssue {
    data object InvalidMemorySize : RepositoryConfigIssue
    data object InvalidMinutes : RepositoryConfigIssue
    data object InvalidVariableName : RepositoryConfigIssue
    data object ReservedVariableName : RepositoryConfigIssue
    data object InvalidVariableValue : RepositoryConfigIssue
    data object DuplicateVariableName : RepositoryConfigIssue
    data object UnclosedPlaceholder : RepositoryConfigIssue
    data class UnknownPlaceholder(val placeholder: String) : RepositoryConfigIssue
    data object OwnPortInSharedVariable : RepositoryConfigIssue
    data class UnknownMultirepoRepository(val alias: String) : RepositoryConfigIssue
    data object EnvFileOutsideWorktree : RepositoryConfigIssue
    data object EnvFileInvalidCharacters : RepositoryConfigIssue
}

object RepositoryConfigRules {
    private val MEMORY_SIZE = Regex("[0-9]+[kKmMgG]")
    private val WHOLE_NUMBER = Regex("[0-9]+")
    private val GRADLE_PROJECT_PATH = Regex("(:[A-Za-z0-9_][A-Za-z0-9_.-]*)+")

    fun isMemorySize(value: String): Boolean = MEMORY_SIZE.matches(value)

    fun isGradleProjectPath(value: String): Boolean = GRADLE_PROJECT_PATH.matches(value)

    fun positiveWholeNumber(value: String): Int? =
        value.takeIf { WHOLE_NUMBER.matches(it) }?.toIntOrNull()?.takeIf { it > 0 }

    fun memorySizeIssue(value: String): RepositoryConfigIssue? =
        if (isMemorySize(value)) null else RepositoryConfigIssue.InvalidMemorySize

    fun minutesIssue(value: String): RepositoryConfigIssue? =
        if (positiveWholeNumber(value) != null) null else RepositoryConfigIssue.InvalidMinutes

    fun variableNameIssue(name: String): RepositoryConfigIssue? = when {
        !isValidVariableName(name) -> RepositoryConfigIssue.InvalidVariableName
        isReservedVariableName(name) -> RepositoryConfigIssue.ReservedVariableName
        else -> null
    }

    fun templateIssue(name: String, template: String): RepositoryConfigIssue? =
        try {
            parseEnvironmentVariable(name, template)
            null
        } catch (error: InvalidEnvironmentTemplate) {
            error.issue
        }

    fun envFileIssue(value: String): RepositoryConfigIssue? = when {
        hasHiddenCharacters(value) -> RepositoryConfigIssue.EnvFileInvalidCharacters
        !isInsideWorktree(value) -> RepositoryConfigIssue.EnvFileOutsideWorktree
        else -> null
    }

    fun isInsideWorktree(relative: String): Boolean {
        if (relative.isBlank()) return false
        val path = runCatching { Path.of(relative) }.getOrNull() ?: return false
        if (path.isAbsolute || relative.startsWith("/") || relative.startsWith("\\")) return false
        val normalized = path.normalize()
        return normalized.toString().isNotEmpty() && !normalized.startsWith("..")
    }
}

data class EnvironmentEntry(val name: String, val template: String)

data class RepositoryConfigEdit(
    val providers: RepositoryProviders = RepositoryProviders(),
    val environment: List<EnvironmentEntry> = emptyList(),
    val envFile: String? = null,
    val addCopyPatterns: List<String> = emptyList(),
    val addLinkPatterns: List<String> = emptyList(),
)
