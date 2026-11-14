package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ForegroundCommandRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException

class ProcessBuilderForegroundCommandRunner : ForegroundCommandRunner {

    override suspend fun run(command: List<String>, workingDirectory: String): Int? {
        val process = try {
            ProcessBuilder(command).directory(File(workingDirectory)).inheritIO().start()
        } catch (_: IOException) {
            return null
        }
        return try {
            runInterruptible(Dispatchers.IO) { process.waitFor() }
        } catch (cancelled: CancellationException) {
            process.destroy()
            throw cancelled
        }
    }
}
