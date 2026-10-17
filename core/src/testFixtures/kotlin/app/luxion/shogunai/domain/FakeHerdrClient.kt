package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.HerdrWorktreeOpening

class FakeHerdrClient(
    private val alreadyOpen: Set<String> = emptySet(),
    private val failure: Throwable? = null,
    private val runFailure: Throwable? = null,
) : HerdrClient {

    data class Split(val paneId: String, val cwd: String, val environment: List<Pair<String, String>>)

    val opened = mutableListOf<Triple<String, String, String>>()
    val splits = mutableListOf<Split>()
    val closed = mutableListOf<String>()
    val ran = mutableListOf<Pair<String, String>>()

    override suspend fun openWorktree(repository: String, path: String, label: String): HerdrWorktreeOpening {
        failure?.let { throw it }
        opened += Triple(repository, path, label)
        val workspaceId = "w${opened.size}"
        return HerdrWorktreeOpening(workspaceId = workspaceId, rootPaneId = "$workspaceId:p1", alreadyOpen = path in alreadyOpen)
    }

    override suspend fun splitPane(paneId: String, cwd: String, environment: List<Pair<String, String>>): String {
        splits += Split(paneId, cwd, environment)
        return paneId.substringBefore(':') + ":p${splits.size + 1}"
    }

    override suspend fun closePane(paneId: String) {
        closed += paneId
    }

    override suspend fun runInPane(paneId: String, command: String) {
        runFailure?.let { throw it }
        ran += paneId to command
    }
}
