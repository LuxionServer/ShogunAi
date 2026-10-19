package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.ui.isAbsolutePath
import app.luxion.shogunai.ui.relativeIfInsideBase
import java.io.File

internal fun normalizeSecretFile(baseRepositoryPath: String, entry: String): String {
    if (!isAbsolutePath(entry)) return entry
    return relativeIfInsideBase(baseRepositoryPath, entry) ?: entry
}

internal fun worktreesRootOffset(baseRepositoryPath: String, worktreesRoot: String): String? {
    if (baseRepositoryPath.isBlank() || worktreesRoot.isBlank()) return null
    return try {
        val offset = File(baseRepositoryPath).toPath().normalize()
            .relativize(File(worktreesRoot).toPath().normalize())
        val parentSteps = offset.count { it.toString() == ".." }
        val tied = parentSteps == 0 || (parentSteps == 1 && offset.getName(0).toString() == "..")
        offset.toString().replace('\\', '/').takeIf { tied }
    } catch (e: IllegalArgumentException) {
        null
    }
}

internal fun applyWorktreesRootOffset(baseRepositoryPath: String, offset: String): String =
    File(baseRepositoryPath).toPath().resolve(offset).normalize().toString().replace('\\', '/')
