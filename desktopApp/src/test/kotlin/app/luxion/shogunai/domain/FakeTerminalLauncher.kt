package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.executor.TerminalLauncher

/**
 * Doble de [TerminalLauncher] para tests.
 *
 * Registra todos los lanzamientos y delega la respuesta en [responder], de
 * modo que cada test pueda simular éxitos o fallos de arranque del proceso.
 */
class FakeTerminalLauncher(
    private val responder: (command: List<String>, workingDirectory: String) -> Result<Unit> =
        { _, _ -> Result.success(Unit) },
) : TerminalLauncher {

    val launched = mutableListOf<Pair<List<String>, String>>()

    override suspend fun launch(command: List<String>, workingDirectory: String): Result<Unit> {
        launched += command to workingDirectory
        return responder(command, workingDirectory)
    }
}
