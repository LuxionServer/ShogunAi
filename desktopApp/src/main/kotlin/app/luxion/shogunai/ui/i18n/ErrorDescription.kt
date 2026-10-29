package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.usecase.GitFailureCause
import app.luxion.shogunai.domain.usecase.classifyGitFailure

enum class ErrorFix { OpenProjectSettings, OpenPreferences }

data class TechnicalDetails(
    val command: List<String>?,
    val exitCode: Int?,
    val output: String,
) {
    fun asPlainText(): String = listOfNotNull(
        command?.joinToString(" ")?.let { it },
        exitCode?.let { "exit code $it" },
        output.takeIf { it.isNotBlank() },
    ).joinToString("\n")
}

data class ErrorTexts(val summary: String, val nextStep: String)

data class UserFacingError(
    val summary: String,
    val nextStep: String,
    val details: TechnicalDetails? = null,
    val fix: ErrorFix? = null,
)

fun Strings.describe(error: Throwable): UserFacingError {
    val gitCause = if (error is WorktreeError.GitCommandFailed) classifyGitFailure(error.errorOutput) else GitFailureCause.Unknown
    val texts = errorTexts(error, gitCause)
    return UserFacingError(
        summary = texts.summary,
        nextStep = texts.nextStep,
        details = technicalDetailsOf(error),
        fix = fixOf(error, gitCause),
    )
}

private fun fixOf(error: Throwable, gitCause: GitFailureCause): ErrorFix? = when (error) {
    is WorktreeError.BaseRepositoryNotFound,
    is WorktreeError.RepositoryNotFound,
    is WorktreeError.SecretFileNotFound,
    WorktreeError.EmulatorProviderNotDeclared,
    WorktreeError.IosSimulatorProviderNotDeclared,
    -> ErrorFix.OpenProjectSettings
    is WorktreeError.ResourcePoolFull -> ErrorFix.OpenPreferences
    is WorktreeError.GitCommandFailed ->
        if (gitCause == GitFailureCause.NotARepository) ErrorFix.OpenProjectSettings else null
    else -> null
}

private fun technicalDetailsOf(error: Throwable): TechnicalDetails? = when (error) {
    is WorktreeError.GitCommandFailed -> TechnicalDetails(error.command, error.exitCode, error.errorOutput)
    is WorktreeError.AppleToolFailed -> TechnicalDetails(error.command, error.exitCode, error.output)
    is WorktreeError.EmulatorExitedEarly -> TechnicalDetails(emptyList(), null, error.lastLogLines)
    is WorktreeError.AndroidToolFailed -> TechnicalDetails(error.command, error.exitCode, error.output)
    is WorktreeError.ProcessListingFailed -> TechnicalDetails(error.command, error.exitCode, "")
    is WorktreeError.WorktreeFolderInUse -> TechnicalDetails(error.command, error.exitCode, error.errorOutput)
    is WorktreeError.HerdrCommandFailed -> TechnicalDetails(error.command, null, error.detail)
    is WorktreeError.HerdrAgentNotStarted -> TechnicalDetails(null, null, "pane: ${error.paneId}\ncommand: ${error.command}")
    is WorktreeError.OrcaCommandFailed -> TechnicalDetails(error.command, null, error.detail)
    is WorktreeError.OrcaAgentNotStarted -> TechnicalDetails(null, null, "terminal: ${error.terminalHandle}\ncommand: ${error.command}")
    is WorktreeError.TerminalLaunchFailed -> TechnicalDetails(null, null, error.detail)
    is WorktreeError.EditorLaunchFailed -> TechnicalDetails(null, null, error.detail)
    is WorktreeError.ClaudeDesktopLaunchFailed -> TechnicalDetails(null, null, error.detail)
    is WorktreeError.FolderOpenFailed -> TechnicalDetails(null, null, error.detail)
    is WorktreeError.PostCreateCommandFailed -> TechnicalDetails(error.command, error.exitCode, error.errorOutput)
    is WorktreeError.InvalidRepositoryConfig -> error.detail?.let { TechnicalDetails(null, null, it) }
    is WorktreeError.AndroidSdkNotFound -> TechnicalDetails(null, null, error.checked.joinToString("\n"))
    is WorktreeError.SecretCopyFailed -> causeDetails(error)
    is WorktreeError.ClipboardWriteFailed -> causeDetails(error)
    is WorktreeError.EnvironmentFileWriteFailed -> causeDetails(error)
    is CommandExecutionException -> TechnicalDetails(error.command, null, error.cause?.message.orEmpty())
    is WorktreeError -> null
    else -> error.message?.let { TechnicalDetails(null, null, it) }
}

private fun causeDetails(error: Throwable): TechnicalDetails? =
    error.cause?.message?.let { TechnicalDetails(null, null, it) }
