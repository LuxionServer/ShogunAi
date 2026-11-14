package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.BuildTurnStore
import app.luxion.shogunai.domain.io.ForegroundCommandRunner
import app.luxion.shogunai.domain.model.BuildSlot

class FakeBuildTurnStore(var slots: List<BuildSlot> = emptyList()) : BuildTurnStore {

    override fun read(): List<BuildSlot> = slots

    override fun write(slots: List<BuildSlot>) {
        this.slots = slots
    }
}

class FakeForegroundCommandRunner(
    private val exitCode: Int? = 0,
    private val whileRunning: suspend () -> Unit = {},
) : ForegroundCommandRunner {

    val runs = mutableListOf<Pair<List<String>, String>>()

    override suspend fun run(command: List<String>, workingDirectory: String): Int? {
        runs += command to workingDirectory
        whileRunning()
        return exitCode
    }
}
