package app.luxion.shogunai.domain.model

/**
 * Un worktree activo, tal y como lo reporta `git worktree list`.
 *
 * @param path Ruta absoluta del worktree.
 * @param branch Rama asociada, o `null` si el worktree está en estado `detached`.
 * @param head SHA del commit al que apunta HEAD, o `null` si no se conoce.
 * @param isMain `true` si es el worktree principal (el repositorio base).
 * @param isBare `true` si es un repositorio bare.
 */
data class Worktree(
    val path: String,
    val branch: String?,
    val head: String? = null,
    val isMain: Boolean = false,
    val isBare: Boolean = false,
)
