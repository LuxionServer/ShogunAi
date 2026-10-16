package app.luxion.shogunai.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.WorktreeError

interface Strings {
    val languageName: String

    val themeLight: String
    val themeDark: String
    val themeSystem: String

    val reload: String
    val cancel: String
    val delete: String
    val browse: String
    val expand: String
    val collapse: String

    val selectFolderDialogTitle: String
    val selectSecretFilesDialogTitle: String

    val projectsTitle: String
    val newProject: String
    val search: String
    val noProjectsYet: String
    fun noProjectsMatch(query: String): String
    val editProject: String
    val deleteProject: String
    val deleteProjectTitle: String
    fun deleteProjectMessage(projectName: String): String

    val projectConfigTitle: String
    val identitySection: String
    val nameLabel: String
    val nameEmptyError: String
    val pathsSection: String
    val baseRepositoryPathLabel: String
    val baseRepositoryPathEmptyError: String
    val worktreesRootLabel: String
    val mainBranchLabel: String
    val mainBranchPlaceholder: String
    val secretFilesSection: String
    val removeSecretFile: String
    val newSecretFileLabel: String
    val add: String
    val agentSection: String
    val agentCommandLabel: String
    val useHeadroomLabel: String
    val headroomDescription: String
    val save: String
    val discardChangesTitle: String
    val discardChangesMessage: String
    val discard: String
    val keepEditing: String

    val closeError: String
    val closeMessage: String
    val newWorktreeSection: String
    val newBranchMode: String
    val existingBranchMode: String
    val taskIdLabel: String
    val taskIdHint: String
    val invalidTaskId: String
    val createWorktree: String
    val loadingBranches: String
    val noBranchesAvailable: String
    fun branchAlreadyHasWorktree(branch: String): String
    val selectBranchPlaceholder: String
    val otherBranchesGroup: String
    val worktreesTitle: String
    val removeWorktreeTitle: String
    fun removeWorktreeMessage(path: String): String
    val forceRemoveTitle: String
    fun forceRemoveMessage(path: String): String
    fun alsoDeleteLocalBranch(branch: String): String
    val forceRemove: String
    val detachedHead: String
    fun uncommittedChanges(count: Int): String
    val upstream: String
    fun mergedInto(branch: String): String
    val statusUnavailable: String
    val folderMissing: String
    val locked: String
    val cleanUp: String
    val cleanUpTitle: String
    fun cleanUpMessage(count: Int): String
    val alsoDeleteLocalBranches: String
    fun worktreesCleanedUp(count: Int): String
    fun cleanupFailed(path: String, detail: String): String
    fun branchKept(branch: String): String
    val justNow: String
    fun minutesAgo(count: Long): String
    fun hoursAgo(count: Long): String
    fun daysAgo(count: Long): String
    fun monthsAgo(count: Long): String
    fun yearsAgo(count: Long): String
    val copied: String
    val copyCommand: String
    fun worktreeCreated(path: String): String
    val worktreesRepairedAfterMove: String

    fun baseRepositoryNotFound(path: String): String
    fun worktreeAlreadyExists(path: String): String
    fun invalidTaskIdError(rawInput: String): String
    fun secretFilesNotFound(files: List<String>, basePath: String): String
    fun gitCommandFailed(command: List<String>, exitCode: Int): String
    fun secretCopyFailed(detail: String?): String
    fun clipboardWriteFailed(detail: String?): String
    val gitLfsNotFound: String
    fun branchNotFound(branch: String): String
    fun branchAlreadyCheckedOut(branch: String, path: String?): String
    fun commandCouldNotStart(command: List<String>): String
    fun unexpectedError(detail: String?): String
}

val LocalStrings = staticCompositionLocalOf<Strings> { EnglishStrings }

fun AppLanguage.strings(): Strings = when (this) {
    AppLanguage.ENGLISH -> EnglishStrings
    AppLanguage.SPANISH -> SpanishStrings
}

fun Strings.worktreeError(error: Throwable): String = when (error) {
    is WorktreeError -> worktreeErrorMessage(error)
    is CommandExecutionException -> commandCouldNotStart(error.command)
    else -> unexpectedError(error.message)
}

private fun Strings.worktreeErrorMessage(error: WorktreeError): String = when (error) {
    is WorktreeError.BaseRepositoryNotFound -> baseRepositoryNotFound(error.path)
    is WorktreeError.WorktreeAlreadyExists -> worktreeAlreadyExists(error.path)
    is WorktreeError.InvalidTaskId -> invalidTaskIdError(error.rawInput)
    is WorktreeError.SecretFileNotFound -> secretFilesNotFound(error.files, error.basePath)
    is WorktreeError.GitCommandFailed -> gitCommandFailed(error.command, error.exitCode) +
        if (error.errorOutput.isNotBlank()) "\n${error.errorOutput}" else ""
    is WorktreeError.SecretCopyFailed -> secretCopyFailed(error.cause?.message)
    is WorktreeError.ClipboardWriteFailed -> clipboardWriteFailed(error.cause?.message)
    WorktreeError.GitLfsNotFound -> gitLfsNotFound
    is WorktreeError.BranchNotFound -> branchNotFound(error.branch)
    is WorktreeError.BranchAlreadyCheckedOut -> branchAlreadyCheckedOut(error.branch, error.path)
}
