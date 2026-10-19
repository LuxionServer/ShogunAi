package app.luxion.shogunai.domain.usecase

import java.nio.file.Path

internal fun sameDirectory(first: String, second: String): Boolean {
    val left = resolvedPath(first)
    val right = resolvedPath(second)
    return if (CASE_INSENSITIVE_FILE_SYSTEM) left.equals(right, ignoreCase = true) else left == right
}

private fun resolvedPath(path: String): String =
    normalizePath(runCatching { Path.of(path).toRealPath().toString() }.getOrDefault(path))

private val CASE_INSENSITIVE_FILE_SYSTEM = System.getProperty("os.name").let {
    it.startsWith("Mac") || it.startsWith("Windows")
}
