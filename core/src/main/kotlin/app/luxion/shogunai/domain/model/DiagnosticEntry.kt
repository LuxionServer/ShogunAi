package app.luxion.shogunai.domain.model

import java.time.Instant

enum class DiagnosticKind { UNHANDLED_ERROR, SHOWN_ERROR, FREEZE }

data class DiagnosticEntry(
    val timestamp: Instant,
    val kind: DiagnosticKind,
    val message: String,
    val stackTrace: String? = null,
)
