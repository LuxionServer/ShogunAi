package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.ClaudeSessionRegistry
import app.luxion.shogunai.domain.model.ClaudeSession

class FakeClaudeSessionRegistry(
    var sessions: List<ClaudeSession> = emptyList(),
    private val failure: Throwable? = null,
    private val started: () -> List<ClaudeSession> = { emptyList() },
) : ClaudeSessionRegistry {

    var listings = 0
        private set

    override fun list(): List<ClaudeSession> {
        listings++
        failure?.let { throw it }
        return sessions + started()
    }
}
