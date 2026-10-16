package app.luxion.shogunai.cli

import app.luxion.shogunai.infrastructure.JsonProjectRepository
import app.luxion.shogunai.infrastructure.NioEnvironmentStateStore
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor
import app.luxion.shogunai.infrastructure.ProcessGradleDaemonSource
import app.luxion.shogunai.infrastructure.RepositoryProvisioningFiles
import app.luxion.shogunai.infrastructure.SocketPortProbe
import app.luxion.shogunai.infrastructure.defaultGradleUserHome
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runShogun(args))
}

fun runShogun(args: Array<String>): Int {
    val executor = ProcessBuilderShellCommandExecutor()
    val gradleUserHome = defaultGradleUserHome()
    val exitCode = ShogunCli(
        executor = executor,
        fileManager = NioFileManager(),
        projectRepository = JsonProjectRepository(),
        provisioningSource = RepositoryProvisioningFiles(),
        daemonSource = ProcessGradleDaemonSource(executor, gradleUserHome),
        gradleUserHome = gradleUserHome,
        environmentStateStore = NioEnvironmentStateStore(),
        portProbe = SocketPortProbe(),
        workingDirectory = System.getProperty("user.dir"),
        out = System.out,
        err = System.err,
        readInput = { System.`in`.bufferedReader().readText() },
    ).run(args.toList())
    System.out.flush()
    System.err.flush()
    return exitCode
}
