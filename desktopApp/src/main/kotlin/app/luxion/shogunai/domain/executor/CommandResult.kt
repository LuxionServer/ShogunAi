package app.luxion.shogunai.domain.executor

/**
 * Resultado de la ejecución de un comando de sistema.
 *
 * Captura la salida estándar, la salida de error y el código de salida,
 * sin interpretar su significado: cada caso de uso decide qué considera éxito.
 */
data class CommandResult(
    val command: List<String>,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    /** Un código de salida 0 es la convención POSIX de éxito en macOS y Linux. */
    val isSuccess: Boolean get() = exitCode == 0
}
