package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.ui.i18n.Strings

internal fun settingsTitle(strings: Strings, isNewProject: Boolean, savedName: String): String =
    if (isNewProject) strings.addProjectTitle else strings.projectSettingsTitle(savedName)

internal fun provisioningRows(
    declared: RepositoryProvisioning?,
    pendingPatterns: List<String>,
    strings: Strings,
): List<Pair<String, String>> {
    val config = RepositoryProvisioning.CONFIG_FILE_NAME
    return declared?.copyPatterns.orEmpty().map { it to config } +
        declared?.linkPatterns.orEmpty().map { it to "$config · ${strings.linkedPattern}" } +
        declared?.worktreeIncludePatterns.orEmpty().map { it to RepositoryProvisioning.WORKTREE_INCLUDE_FILE_NAME } +
        pendingPatterns.map { it to "$config · ${strings.pendingSave}" }
}
