package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

/**
 * Configuración de un proyecto sobre el que se crean worktrees.
 *
 * Modela de forma reutilizable la configuración de un repositorio: podremos
 * apuntar a distintos repositorios sin tocar la lógica de los casos de uso.
 *
 * @param baseRepositoryPath Ruta absoluta del repositorio principal
 *   (p. ej. `~/projects/main-repo`), desde donde se ejecuta Git.
 * @param worktreesRoot Carpeta padre donde se crean los worktrees
 *   (p. ej. `~/projects`).
 * @param secretFiles Archivos de credenciales locales que hay que copiar al
 *   nuevo worktree para poder compilar (p. ej. `local.properties`).
 */
@Serializable
data class ProjectConfig(
    val baseRepositoryPath: String,
    val worktreesRoot: String,
    val secretFiles: List<String>,
) {
    /** Ruta del worktree para una tarea: `<worktreesRoot>/<taskId>`. */
    fun worktreePathFor(taskId: String): String = joinPath(worktreesRoot, taskId)

    /** Ruta de un archivo de secretos dentro del repositorio base. */
    fun baseSecretPath(fileName: String): String = joinPath(baseRepositoryPath, fileName)
}

/**
 * Une segmentos de ruta con `/`. En macOS y Linux el separador es el mismo, por
 * lo que no necesitamos `java.nio` para las rutas controladas que manejamos aquí.
 */
internal fun joinPath(vararg parts: String): String =
    parts.joinToString("/") { it.trimEnd('/') }
