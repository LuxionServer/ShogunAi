package app.luxion.shogunai.domain.usecase

import java.nio.file.Path

internal fun sameDirectory(first: String, second: String): Boolean {
    val left = resolvedPath(first)
    val right = resolvedPath(second)
    return if (CASE_INSENSITIVE_FILE_SYSTEM) left.equals(right, ignoreCase = true) else left == right
}

internal fun isInsideDirectory(directory: String, root: String): Boolean {
    val inner = normalizePath(directory)
    val outer = normalizePath(root)
    return inner.equals(outer, ignoreCase = CASE_INSENSITIVE_FILE_SYSTEM) ||
        inner.startsWith("$outer/", ignoreCase = CASE_INSENSITIVE_FILE_SYSTEM)
}

internal fun canonicalDirectory(path: String): String = resolvedPath(path)

private fun resolvedPath(path: String): String =
    normalizePath(runCatching { Path.of(path).toRealPath().toString() }.getOrDefault(path))

private val CASE_INSENSITIVE_FILE_SYSTEM = System.getProperty("os.name").let {
    it.startsWith("Mac") || it.startsWith("Windows")
}
