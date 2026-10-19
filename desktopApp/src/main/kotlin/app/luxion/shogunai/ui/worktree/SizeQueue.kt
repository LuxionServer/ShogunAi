package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SizeQueue(private val scope: CoroutineScope) {
    private val pending = mutableListOf<Request>()
    private var prioritized: Set<Any> = emptySet()
    private var worker: Job? = null

    fun submit(
        owner: Any,
        worktrees: List<Worktree>,
        measure: suspend (Worktree) -> Long?,
        deliver: (path: String, size: Long) -> Unit,
        replacePending: Boolean = true,
    ) {
        if (replacePending) pending.removeAll { it.owner == owner }
        val queued = pending.filter { it.owner == owner }.map { it.worktree.path }.toSet()
        pending += worktrees.filterNot { it.path in queued }.map { Request(owner, it, measure, deliver) }
        if (worker?.isActive != true) worker = scope.launch { drain() }
    }

    fun cancel(owner: Any) {
        pending.removeAll { it.owner == owner }
    }

    fun prioritize(owners: Set<Any>) {
        prioritized = owners
    }

    private suspend fun drain() {
        while (pending.isNotEmpty()) {
            val next = pending.firstOrNull { it.owner in prioritized } ?: pending.first()
            pending.remove(next)
            next.measure(next.worktree)?.let { next.deliver(next.worktree.path, it) }
        }
    }

    private class Request(
        val owner: Any,
        val worktree: Worktree,
        val measure: suspend (Worktree) -> Long?,
        val deliver: (String, Long) -> Unit,
    )
}
