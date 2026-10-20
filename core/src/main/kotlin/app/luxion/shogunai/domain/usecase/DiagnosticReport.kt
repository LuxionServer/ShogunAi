package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DiagnosticsLog
import app.luxion.shogunai.domain.model.DiagnosticEntry

class DiagnosticReport(
    private val version: String,
    private val log: DiagnosticsLog,
    private val systemProperty: (String) -> String? = System::getProperty,
) {
    fun build(): String {
        val entries = log.recent(ENTRY_LIMIT)
        return buildString {
            appendLine("ShogunAi diagnostic report")
            appendLine("Version: $version")
            appendLine("Operating system: ${property("os.name")} ${property("os.version")}")
            appendLine("Architecture: ${property("os.arch")}")
            appendLine("Java runtime: ${property("java.runtime.version")}")
            appendLine()
            if (entries.isEmpty()) {
                append("No errors were recorded.")
            } else {
                appendLine("Recent entries (${entries.size}):")
                entries.forEach { appendLine(format(it)) }
            }
        }.trimEnd()
    }

    private fun property(name: String): String = systemProperty(name).orEmpty().ifBlank { "unknown" }

    private fun format(entry: DiagnosticEntry): String =
        "${entry.timestamp} [${entry.kind}] ${entry.message}" + entry.stackTrace?.let { "\n$it" }.orEmpty()

    private companion object {
        const val ENTRY_LIMIT = 50
    }
}
