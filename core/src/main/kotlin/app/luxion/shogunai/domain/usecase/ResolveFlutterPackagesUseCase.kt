package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.FlutterPubGet
import app.luxion.shogunai.domain.model.FlutterPubGetFailure
import app.luxion.shogunai.domain.model.joinPath
import kotlinx.coroutines.CancellationException

fun interface FlutterPackagesPreparer {
    suspend fun prepare(repositoryPath: String, worktreePath: String): FlutterPubGet?

    companion object {
        val NONE = FlutterPackagesPreparer { _, _ -> null }
    }
}

fun usesFvm(fileManager: FileManager, folder: String): Boolean = fileManager.exists(joinPath(folder, FVM_CONFIG))

fun flutterCommand(fileManager: FileManager, folder: String): List<String> =
    if (usesFvm(fileManager, folder)) listOf("fvm", "flutter") else listOf("flutter")

private const val FVM_CONFIG = ".fvmrc"

class ResolveFlutterPackagesUseCase(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
) : FlutterPackagesPreparer {

    override suspend fun prepare(repositoryPath: String, worktreePath: String): FlutterPubGet? = try {
        if (provisioningSource.read(repositoryPath).providers.flutter == null) null else pubGet(worktreePath)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        FlutterPubGet.Needed(FlutterPubGetFailure.Unexpected(error.message ?: error::class.simpleName.orEmpty()))
    }

    private suspend fun pubGet(worktreePath: String): FlutterPubGet {
        val command = flutterCommand(fileManager, worktreePath) + listOf("pub", "get")
        val result = try {
            executor.execute(command = command, workingDirectory = worktreePath)
        } catch (error: CommandExecutionException) {
            return FlutterPubGet.Needed(FlutterPubGetFailure.NotFound(command.first()))
        }
        if (result.isSuccess) return FlutterPubGet.Resolved
        val errorLines = result.stderr.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.takeLast(ERROR_LINES)
        return FlutterPubGet.Needed(FlutterPubGetFailure.Exited(result.exitCode, errorLines))
    }

    private companion object {
        const val ERROR_LINES = 5
    }
}
