package app.luxion.shogunai.domain.model

/**
 * Tipo de rama a crear, según el flujo de worktrees.
 * El prefijo se antepone al ID de la tarea: `feature/TASK-123`, `fix/TASK-456`.
 */
enum class BranchType(val prefix: String) {
    FEATURE("feature"),
    FIX("fix"),
}
