package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.MultirepoSource
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.MultirepoDeclaration

class FakeMultirepoSource(
    declarations: Map<String, MultirepoDeclaration> = emptyMap(),
    known: List<String> = declarations.keys.toList(),
    var approveAll: Boolean = true,
) : MultirepoSource {

    val declarations = declarations.toMutableMap()
    val known = known.toMutableList()
    val unreadable = mutableMapOf<String, Exception>()
    val missingFolders = mutableSetOf<String>()
    val approvals = mutableMapOf<String, String>()
    val agents = mutableMapOf<String, AgentLaunchConfig>()

    override fun read(folder: String): MultirepoDeclaration? {
        unreadable[folder]?.let { throw it }
        return declarations[folder]
    }

    override fun write(folder: String, declaration: MultirepoDeclaration) {
        declarations[folder] = declaration
    }

    override fun delete(folder: String) {
        declarations.remove(folder)
    }

    override fun knownFolders(): List<String> = known.toList()

    override fun remember(folder: String) {
        if (folder !in known) known += folder
    }

    override fun forget(folder: String) {
        known.remove(folder)
        approvals.remove(folder)
        agents.remove(folder)
    }

    override fun isApproved(folder: String, subject: String): Boolean = approveAll || approvals[folder] == subject

    override fun approve(folder: String, subject: String) {
        approvals[folder] = subject
    }

    override fun agent(folder: String): AgentLaunchConfig? = agents[folder]

    override fun setAgent(folder: String, agent: AgentLaunchConfig) {
        agents[folder] = agent
    }

    override fun forgetMissing(): List<String> {
        val missing = known.filter { it in missingFolders }
        missing.forEach(::forget)
        return missing
    }
}
