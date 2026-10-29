package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.HerdrConfigSource
import java.io.File

class HerdrConfigFile(
    private val environmentVariable: (String) -> String?,
    private val userHome: String,
    private val osName: String = System.getProperty("os.name").orEmpty(),
) : HerdrConfigSource {

    private val isWindows: Boolean get() = osName.startsWith("Windows", ignoreCase = true)

    override fun worktreesRoot(): String {
        val configured = runCatching { File(configPath()).takeIf { it.isFile }?.readLines() }.getOrNull()
            ?.let(::worktreesDirectory)
        return expandHome(configured ?: DEFAULT_WORKTREES_ROOT)
    }

    fun configPath(): String {
        environmentVariable(CONFIG_PATH_VARIABLE)?.takeIf { it.isNotBlank() }?.let { return it }
        val configDirectory = environmentVariable("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            ?: environmentVariable("APPDATA")?.takeIf { isWindows && it.isNotBlank() }
            ?: File(userHome, ".config").path
        return File(File(configDirectory, "herdr"), "config.toml").path
    }

    private fun expandHome(path: String): String = when {
        path == "~" -> userHome
        path.startsWith("~/") || path.startsWith("~\\") -> File(userHome, path.drop(2)).path
        else -> path
    }

    companion object {
        const val DEFAULT_WORKTREES_ROOT = "~/.herdr/worktrees"
        private const val CONFIG_PATH_VARIABLE = "HERDR_CONFIG_PATH"
        private val TABLE_HEADER = Regex("""^\[\s*([A-Za-z0-9_.-]+)\s*]\s*(#.*)?$""")
        private val DIRECTORY_ENTRY = Regex("""^directory\s*=\s*(?:"((?:[^"\\]|\\.)*)"|'([^']*)')\s*(#.*)?$""")

        fun worktreesDirectory(lines: List<String>): String? {
            var table: String? = null
            lines.map { it.trim() }.forEach { line ->
                TABLE_HEADER.matchEntire(line)?.let {
                    table = it.groupValues[1]
                    return@forEach
                }
                if (table != "worktrees") return@forEach
                DIRECTORY_ENTRY.matchEntire(line)?.let { match ->
                    val basic = match.groups[1]?.value
                    val value = basic?.let(::unescape) ?: match.groupValues[2]
                    return value.takeIf { it.isNotBlank() }
                }
            }
            return null
        }

        private fun unescape(value: String): String = value.replace("\\\\", "\u0000").replace("\\\"", "\"").replace("\u0000", "\\")
    }
}
