package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.KeptDaemon
import app.luxion.shogunai.domain.model.Worktree

data class GradleTrim(
    val limit: Int?,
    val stopped: List<DaemonProcess> = emptyList(),
    val kept: List<KeptDaemon> = emptyList(),
    val remaining: Int = 0,
)

class TrimGradleDaemonsUseCase(
    private val listDaemons: ListGradleDaemonsUseCase,
    private val stopDaemons: StopGradleDaemonsUseCase,
    private val limits: ResolveResourceLimitsUseCase,
) {
    suspend operator fun invoke(worktrees: List<Worktree>? = null): Result<GradleTrim> = runCatching {
        val limit = limits().gradleDaemons
        val daemons = listDaemons(worktrees).getOrThrow().filter { it.process.kind == DaemonKind.GRADLE }
        if (limit == null || daemons.size <= limit) return@runCatching GradleTrim(limit, remaining = daemons.size)
        val candidates = daemons.map { it.process }
            .filter { it.state is DaemonState.Idle }
            .sortedBy { (it.state as DaemonState.Idle).since }
            .take(daemons.size - limit)
        if (candidates.isEmpty()) return@runCatching GradleTrim(limit, remaining = daemons.size)
        val report = stopDaemons(candidates.map { it.pid }).getOrThrow()
        GradleTrim(limit, report.stopped, report.kept, daemons.size - report.stopped.size)
    }
}
