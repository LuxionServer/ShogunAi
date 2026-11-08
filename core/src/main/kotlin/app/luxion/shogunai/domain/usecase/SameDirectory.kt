package app.luxion.shogunai.domain.usecase

import java.nio.file.Path

internal fun sameDirectory(first: String, second: String): Boolean =
    resolvedPath(first).equals(resolvedPath(second), ignoreCase = CASE_INSENSITIVE_FILE_SYSTEM)

internal fun isInsideDirectory(directory: String, root: String): Boolean {
    val inner = normalizePath(directory)
    val outer = normalizePath(root)
    return inner.equals(outer, ignoreCase = CASE_INSENSITIVE_FILE_SYSTEM) ||
        inner.startsWith("$outer/", ignoreCase = CASE_INSENSITIVE_FILE_SYSTEM)
}

internal fun canonicalDirectory(path: String): String = resolvedPath(path)

private fun resolvedPath(path: String): String =
    normalizePath(runCatching { Path.of(path).toRealPath().toString() }.getOrDefault(path))

internal fun isCaseInsensitiveFileSystem(osName: String): Boolean = osName.startsWith("Mac") || osName.startsWith("Windows")

private val CASE_INSENSITIVE_FILE_SYSTEM = isCaseInsensitiveFileSystem(System.getProperty("os.name"))
