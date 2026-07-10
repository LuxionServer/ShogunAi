package app.luxion.shogunai.domain.executor

/**
 * Puerto de dominio para ejecutar comandos de sistema de forma asíncrona.
 *
 * La implementación concreta vive en la capa de infraestructura, de modo que
 * los casos de uso puedan probarse sustituyendo este puerto por un doble.
 *
 * El comando se pasa como lista de argumentos (argv), no como una cadena que
 * un shell tenga que interpretar. Así evitamos:
 *  - Diferencias entre el shell por defecto de macOS (zsh) y Arch Linux (bash).
 *  - Problemas de escapado y de inyección de comandos.
 * Invocamos el binario directamente (p. ej. `git`), no `sh -c "..."`.
 */
interface ShellCommandExecutor {

    /**
     * Ejecuta [command] y devuelve su [CommandResult] cuando el proceso termina.
     *
     * @param command Argumentos del comando, empezando por el ejecutable.
     * @param workingDirectory Directorio de trabajo del proceso; si es `null`,
     *   se hereda el del proceso actual.
     * @throws CommandExecutionException si el proceso no puede lanzarse
     *   (por ejemplo, el ejecutable no existe en el PATH).
     */
    suspend fun execute(
        command: List<String>,
        workingDirectory: String? = null,
    ): CommandResult
}

/**
 * Se lanza cuando el sistema operativo no puede iniciar el proceso.
 * Es distinto de un comando que arranca y devuelve un código de salida != 0:
 * eso último se refleja en [CommandResult.exitCode].
 */
class CommandExecutionException(
    val command: List<String>,
    cause: Throwable,
) : Exception("No se pudo ejecutar el comando: ${command.joinToString(" ")}", cause)
