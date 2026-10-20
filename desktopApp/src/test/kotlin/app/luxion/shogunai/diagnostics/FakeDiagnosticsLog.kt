package app.luxion.shogunai.diagnostics

import app.luxion.shogunai.domain.io.DiagnosticsLog
import app.luxion.shogunai.domain.model.DiagnosticEntry
import java.util.concurrent.CopyOnWriteArrayList

class FakeDiagnosticsLog : DiagnosticsLog {
    val entries = CopyOnWriteArrayList<DiagnosticEntry>()
    override fun record(entry: DiagnosticEntry) {
        entries += entry
    }
    override fun recent(limit: Int): List<DiagnosticEntry> = entries.takeLast(limit)
}
