package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.TerminalLauncher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Implementación de [TerminalLauncher] sobre [ProcessBuilder].
 *
 * A diferencia de [ProcessBuilderShellCommandExecutor], no espera a que el
 * proceso termine: el objetivo es abrir la ventana de terminal y devolver el
 * control en cuanto arranca.
 */
class ProcessTerminalLauncher(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TerminalLauncher {

    override suspend fun launch(command: List<String>, workingDirectory: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                ProcessBuilder(command).apply { directory(File(workingDirectory)) }.start()
                Unit
            }
        }
}
