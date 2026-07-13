package app.luxion.shogunai.domain.model

/**
 * Errores de dominio del flujo de worktrees.
 *
 * Los casos de uso devuelven `Result<T>`; cuando fallan de forma esperada, el
 * fallo lleva una de estas variantes, de modo que la capa superior (la futura
 * GUI) pueda decidir el mensaje a mostrar sin inspeccionar cadenas de texto.
 */
sealed class WorktreeError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** El repositorio base configurado no existe en disco. */
    class BaseRepositoryNotFound(val path: String) :
        WorktreeError("El repositorio base no existe: $path")

    /** Ya hay un directorio en la ruta destino del worktree. */
    class WorktreeAlreadyExists(val path: String) :
        WorktreeError("El directorio del worktree ya existe: $path")

    /** Faltan uno o más archivos de secretos en el repositorio base. */
    class SecretFileNotFound(val files: List<String>, val basePath: String) :
        WorktreeError("No se encontraron archivos de secretos en $basePath: ${files.joinToString()}")

    /** Un comando de Git terminó con un código de salida distinto de cero. */
    class GitCommandFailed(
        val command: List<String>,
        val exitCode: Int,
        val errorOutput: String,
    ) : WorktreeError(
        "El comando git falló (código $exitCode): ${command.joinToString(" ")}" +
            if (errorOutput.isNotBlank()) "\n$errorOutput" else "",
    )

    /** Falló la copia de algún archivo de secretos tras crear el worktree. */
    class SecretCopyFailed(cause: Throwable) :
        WorktreeError("Falló la copia de archivos de secretos: ${cause.message}", cause)

    /** No hay ningún emulador de terminal disponible para abrir el worktree. */
    object NoTerminalAvailable :
        WorktreeError("No se encontró ningún emulador de terminal disponible")

    /** El sistema operativo no pudo iniciar el proceso de la terminal. */
    class TerminalLaunchFailed(cause: Throwable) :
        WorktreeError("Falló el lanzamiento de la terminal: ${cause.message}", cause)

    /** El repositorio requiere Git LFS pero el binario `git-lfs` no está instalado. */
    object GitLfsNotFound : WorktreeError(
        "El repositorio requiere Git LFS pero 'git-lfs' no está instalado. " +
            "Instalalo desde https://git-lfs.com y volvé a intentarlo.",
    )
}
