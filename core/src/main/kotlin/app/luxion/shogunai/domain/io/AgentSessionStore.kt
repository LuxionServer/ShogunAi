package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.AgentSessionRecord

interface AgentSessionStore {

    fun write(worktreePath: String, record: AgentSessionRecord)

    fun remove(worktreePath: String, sessionId: String)

    fun list(worktreePath: String): List<AgentSessionRecord>
}
