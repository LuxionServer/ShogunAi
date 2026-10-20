package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.DiagnosticEntry

interface DiagnosticsLog {

    fun record(entry: DiagnosticEntry)

    fun recent(limit: Int): List<DiagnosticEntry>
}
