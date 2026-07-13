package app.luxion.shogunai.domain.executor

/**
 * Puerto de dominio para abrir una nueva sesión de terminal.
 *
 * A diferencia de [ShellCommandExecutor], no espera a que el proceso termine:
 * el objetivo es lanzar una ventana/pestaña de terminal interactiva y devolver
 * el control en cuanto el proceso arranca.
 */
interface TerminalLauncher {

    /**
     * Lanza [command] (argv) en una nueva terminal con [workingDirectory] como
     * directorio de trabajo.
     *
     * @return éxito en cuanto el proceso del sistema operativo arranca, o
     *   fallo si no pudo iniciarse.
     */
    suspend fun launch(command: List<String>, workingDirectory: String): Result<Unit>
}
