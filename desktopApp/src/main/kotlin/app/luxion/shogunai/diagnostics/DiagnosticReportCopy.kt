package app.luxion.shogunai.diagnostics

suspend fun copyDiagnosticReport(build: suspend () -> String, copy: suspend (String) -> Unit, confirm: () -> Unit) {
    copy(build())
    confirm()
}
