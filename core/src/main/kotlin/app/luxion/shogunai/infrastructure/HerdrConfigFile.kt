package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.HerdrConfigSource
import java.io.File

class HerdrConfigFile(
    private val environmentVariable: (String) -> String?,
    private val userHome: String,
    private val osName: String = System.getProperty("os.name", ""),
) : HerdrConfigSource {

    private val isWindows: Boolean get() = osName.startsWith("Windows", ignoreCase = true)

    override fun worktreesRoot(): String = expandHome(readLines()?.let(::worktreesDirectory) ?: DEFAULT_WORKTREES_ROOT)

    fun notifiesOnDesktop(): Boolean = readLines()?.let { stringEntry(it, "ui.toast", "delivery") } in DESKTOP_DELIVERIES

    private fun readLines(): List<String>? = runCatching { File(configPath()).takeIf { it.isFile }?.readLines() }.getOrNull()

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
        private val DESKTOP_DELIVERIES = setOf("terminal", "system")

        fun worktreesDirectory(lines: List<String>): String? = stringEntry(lines, "worktrees", "directory")?.takeIf { it.isNotBlank() }

        fun stringEntry(lines: List<String>, wantedTable: String, key: String): String? {
            val entry = Regex("""^${Regex.escape(key)}\s*=\s*(?:"((?:[^"\\]|\\.)*)"|'([^']*)')\s*(#.*)?$""")
            var table: String? = null
            lines.map { it.trim() }.forEach { line ->
                TABLE_HEADER.matchEntire(line)?.let {
                    table = it.groupValues[1]
                    return@forEach
                }
                if (table != wantedTable) return@forEach
                entry.matchEntire(line)?.let { match ->
                    val basic = match.groups[1]?.value
                    return if (basic != null) unescape(basic) else match.groupValues[2]
                }
            }
            return null
        }

        private fun unescape(value: String): String = value.replace("\\\\", "\u0000").replace("\\\"", "\"").replace("\u0000", "\\")
    }
}
