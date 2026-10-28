package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

internal const val BASE_BRANCH_KEY = "gh-merge-base"

internal fun baseBranchKey(branch: String): String = "branch.$branch.$BASE_BRANCH_KEY"

internal suspend fun writeBaseBranch(config: ProjectConfig, executor: ShellCommandExecutor, branch: String, baseBranch: String) {
    val result = executor.execute(
        command = listOf("git", "config", baseBranchKey(branch), baseBranch),
        workingDirectory = config.baseRepositoryPath,
    )
    if (!result.isSuccess) throw WorktreeError.GitCommandFailed(result.command, result.exitCode, result.stderr)
}

class ReadBaseBranchesUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(): Result<Map<String, String>> = runCatching {
        val result = executor.execute(
            command = listOf("git", "config", "--get-regexp", BASE_BRANCH_PATTERN),
            workingDirectory = config.baseRepositoryPath,
        )
        when {
            result.isSuccess -> parseBaseBranches(result.stdout)
            result.exitCode == 1 && result.stdout.isBlank() -> emptyMap()
            else -> throw WorktreeError.GitCommandFailed(result.command, result.exitCode, result.stderr)
        }
    }
}

class SetBaseBranchUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(branch: String, baseBranch: String): Result<String> = runCatching {
        val resolved = ResolveBaseBranchUseCase(executor)(config.baseRepositoryPath, baseBranch.trim())
            ?: throw WorktreeError.NotABranch(baseBranch)
        writeBaseBranch(config, executor, branch, resolved)
        resolved
    }
}

internal fun parseBaseBranches(output: String): Map<String, String> =
    output.lineSequence()
        .mapNotNull { line ->
            val key = line.substringBefore(' ')
            val value = line.substringAfter(' ', "").trim()
            val suffix = ".$BASE_BRANCH_KEY"
            if (!key.startsWith("branch.") || !key.endsWith(suffix, ignoreCase = true) || value.isEmpty()) return@mapNotNull null
            val branch = key.removePrefix("branch.").dropLast(suffix.length)
            branch.takeIf { it.isNotEmpty() }?.let { it to value }
        }
        .toMap()

private const val BASE_BRANCH_PATTERN = "^branch\\..*\\.gh-merge-base$"
