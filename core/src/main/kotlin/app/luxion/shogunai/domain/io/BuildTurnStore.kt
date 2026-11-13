package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.BuildSlot

interface BuildTurnStore {

    fun read(): List<BuildSlot>

    fun write(slots: List<BuildSlot>)
}

interface ForegroundCommandRunner {

    suspend fun run(command: List<String>, workingDirectory: String): Int?
}
