package app.luxion.shogunai.cli

import app.luxion.shogunai.infrastructure.JsonProjectRepository
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor
import app.luxion.shogunai.infrastructure.RepositoryProvisioningFiles
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runShogun(args))
}

fun runShogun(args: Array<String>): Int {
    val exitCode = ShogunCli(
        executor = ProcessBuilderShellCommandExecutor(),
        fileManager = NioFileManager(),
        projectRepository = JsonProjectRepository(),
        provisioningSource = RepositoryProvisioningFiles(),
        workingDirectory = System.getProperty("user.dir"),
        out = System.out,
        err = System.err,
        readInput = { System.`in`.bufferedReader().readText() },
    ).run(args.toList())
    System.out.flush()
    System.err.flush()
    return exitCode
}
