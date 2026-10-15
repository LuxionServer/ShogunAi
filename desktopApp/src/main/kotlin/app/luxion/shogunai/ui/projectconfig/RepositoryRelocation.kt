package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.ui.relativeIfInsideBase
import java.io.File

/**
 * Normalizes a secret file entry so it follows the base repository if it moves:
 * an absolute path inside [baseRepositoryPath] becomes relative to it. Relative
 * entries and absolute paths outside the base repository are returned unchanged.
 */
internal fun normalizeSecretFile(baseRepositoryPath: String, entry: String): String {
    if (!File(entry).isAbsolute) return entry
    return relativeIfInsideBase(baseRepositoryPath, entry) ?: entry
}

/**
 * Where [worktreesRoot] sits relative to [baseRepositoryPath], if it is tied to
 * it: inside it (`worktrees`), its parent (`..`) or a sibling (`../worktrees`).
 * Returns `null` for any other location, which shouldn't follow the base
 * repository when it moves.
 */
internal fun worktreesRootOffset(baseRepositoryPath: String, worktreesRoot: String): String? {
    if (baseRepositoryPath.isBlank() || worktreesRoot.isBlank()) return null
    return try {
        val offset = File(baseRepositoryPath).toPath().normalize()
            .relativize(File(worktreesRoot).toPath().normalize())
        val parentSteps = offset.count { it.toString() == ".." }
        val tied = parentSteps == 0 || (parentSteps == 1 && offset.getName(0).toString() == "..")
        offset.toString().takeIf { tied }
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** Applies an [offset] from [worktreesRootOffset] to [baseRepositoryPath]. */
internal fun applyWorktreesRootOffset(baseRepositoryPath: String, offset: String): String =
    File(baseRepositoryPath).toPath().resolve(offset).normalize().toString()
