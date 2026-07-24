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
 * @param agentLaunchConfig Comando de agente a lanzar en la terminal del
 *   worktree. Por defecto, `claude` envuelto con Headroom.
 */
@Serializable
data class ProjectConfig(
    val baseRepositoryPath: String,
    val worktreesRoot: String,
    val secretFiles: List<String>,
    val agentLaunchConfig: AgentLaunchConfig = AgentLaunchConfig(),
) {
    /** Ruta del worktree para una tarea: `<worktreesRoot>/<taskId>`. */
    fun worktreePathFor(taskId: String): String = joinPath(worktreesRoot, taskId)

    /**
     * Ruta del worktree para una rama ya existente.
     *
     * Si la rama sigue la convención `<tipo>/<id>` de [BranchType] (p. ej.
     * `feature/TASK-123`), se descarta el prefijo para que el directorio
     * coincida con el que se habría creado desde el flujo de rama nueva
     * (`TASK-123`). En cualquier otro caso, se reemplaza `/` por `-` para
     * obtener un único segmento de ruta válido (p. ej. `hotfix/x` -> `hotfix-x`).
     */
    fun worktreePathForBranch(branch: String): String {
        val knownPrefix = BranchType.entries.map { "${it.prefix}/" }.firstOrNull { branch.startsWith(it) }
        val sanitized = knownPrefix?.let { branch.removePrefix(it) } ?: branch
        return worktreePathFor(sanitized.replace("/", "-"))
    }

    /** Ruta de un archivo de secretos dentro del repositorio base. */
    fun baseSecretPath(fileName: String): String = joinPath(baseRepositoryPath, fileName)
}

/**
 * Une segmentos de ruta con `/`. En macOS y Linux el separador es el mismo, por
 * lo que no necesitamos `java.nio` para las rutas controladas que manejamos aquí.
 */
internal fun joinPath(vararg parts: String): String =
    parts.joinToString("/") { it.trimEnd('/') }
