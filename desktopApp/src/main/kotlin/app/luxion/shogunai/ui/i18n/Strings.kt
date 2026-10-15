package app.luxion.shogunai.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.WorktreeError

/**
 * Every user-facing text of the UI, one implementation per [AppLanguage].
 *
 * Adding a member forces every language to translate it (missing translations
 * are a compile error). Texts with arguments are functions, so the arguments
 * are type-checked instead of positional format placeholders.
 */
interface Strings {
    /** Name of the language written in that same language, as shown in the language selector. */
    val languageName: String

    // Theme mode selector
    val themeLight: String
    val themeDark: String
    val themeSystem: String

    // Shared
    val reload: String
    val cancel: String
    val delete: String
    val browse: String
    val expand: String
    val collapse: String

    // File chooser dialogs
    val selectFolderDialogTitle: String
    val selectSecretFilesDialogTitle: String

    // Project list
    val projectsTitle: String
    val newProject: String
    val search: String
    val noProjectsYet: String
    fun noProjectsMatch(query: String): String
    val editProject: String
    val deleteProject: String
    val deleteProjectTitle: String
    fun deleteProjectMessage(projectName: String): String

    // Project configuration
    val projectConfigTitle: String
    val identitySection: String
    val nameLabel: String
    val nameEmptyError: String
    val pathsSection: String
    val baseRepositoryPathLabel: String
    val baseRepositoryPathEmptyError: String
    val worktreesRootLabel: String
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

    // Worktrees
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
    val copied: String
    val copyCommand: String
    fun worktreeCreated(path: String): String
    val worktreesRepairedAfterMove: String

    // Worktree errors (see worktreeError)
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
    fun unexpectedError(detail: String?): String
}

/** Strings of the language selected by the user. Provided by `AppRoot`. */
val LocalStrings = staticCompositionLocalOf<Strings> { EnglishStrings }

fun AppLanguage.strings(): Strings = when (this) {
    AppLanguage.ENGLISH -> EnglishStrings
    AppLanguage.SPANISH -> SpanishStrings
}

/**
 * User message for a failed operation. Domain errors are translated from their
 * structured data; Git's own error output is appended verbatim.
 */
fun Strings.worktreeError(error: Throwable): String =
    if (error is WorktreeError) worktreeErrorMessage(error) else unexpectedError(error.message)

// Exhaustive over the sealed hierarchy: a new WorktreeError variant doesn't compile until it's translated.
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
