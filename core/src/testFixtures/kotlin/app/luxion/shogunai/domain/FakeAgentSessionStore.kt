package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.AgentSessionStore
import app.luxion.shogunai.domain.model.AgentSessionRecord

class FakeAgentSessionStore(initial: Map<String, List<AgentSessionRecord>> = emptyMap()) : AgentSessionStore {

    val records: MutableMap<String, MutableList<AgentSessionRecord>> =
        initial.mapValues { it.value.toMutableList() }.toMutableMap()

    override fun write(worktreePath: String, record: AgentSessionRecord) {
        val sessions = records.getOrPut(worktreePath) { mutableListOf() }
        sessions.removeAll { it.sessionId == record.sessionId }
        sessions += record
    }

    override fun remove(worktreePath: String, sessionId: String) {
        records[worktreePath]?.removeAll { it.sessionId == sessionId }
    }

    override fun list(worktreePath: String): List<AgentSessionRecord> = records[worktreePath].orEmpty().toList()
}
