package app.luxion.shogunai.domain.model

sealed interface UnavailableReason {
    data object Missing : UnavailableReason

    data class BrokenLink(val target: String) : UnavailableReason

    data class Unreadable(val detail: String?) : UnavailableReason
}

enum class LocalFileOrigin { PROJECT, REPOSITORY }

data class UnavailableLocalFile(
    val path: String,
    val fullPath: String,
    val origin: LocalFileOrigin,
    val reason: UnavailableReason,
)

internal fun UnavailableLocalFile.problemText(): String = when (val reason = reason) {
    UnavailableReason.Missing -> "not found at $fullPath. Create it there"
    is UnavailableReason.BrokenLink ->
        "$fullPath is a link to ${reason.target}, which doesn't exist. Restore ${reason.target} or replace the link with the file"
    is UnavailableReason.Unreadable ->
        "$fullPath can't be read" + (reason.detail?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") + ". Make it readable for your user"
}

internal fun UnavailableLocalFile.removalText(): String = when (origin) {
    LocalFileOrigin.PROJECT -> "or remove it from the project's local files"
    LocalFileOrigin.REPOSITORY -> "or stop matching it in .shogunai.json or .worktreeinclude"
}

internal fun localFilesUnavailableMessage(basePath: String, files: List<UnavailableLocalFile>): String =
    "These local files can't be copied or linked from $basePath, so nothing was changed:\n" +
        files.joinToString("\n") { "  ${it.path}: ${it.problemText()}, ${it.removalText()}" }
