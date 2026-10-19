package app.luxion.shogunai.cli

import app.luxion.shogunai.infrastructure.FileKmpTestWiring
import app.luxion.shogunai.infrastructure.HerdrConfigFile
import app.luxion.shogunai.infrastructure.JsonProjectRepository
import app.luxion.shogunai.infrastructure.JsonRepositoryTrust
import app.luxion.shogunai.infrastructure.LocalAndroidSdk
import app.luxion.shogunai.infrastructure.MacDerivedDataSource
import app.luxion.shogunai.infrastructure.NioEnvironmentStateStore
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor
import app.luxion.shogunai.infrastructure.ProcessGradleDaemonSource
import app.luxion.shogunai.infrastructure.RepositoryProvisioningFiles
import app.luxion.shogunai.infrastructure.SimctlAppleSimulators
import app.luxion.shogunai.infrastructure.SocketPortProbe
import app.luxion.shogunai.infrastructure.defaultGradleUserHome
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runShogun(args))
}

fun runShogun(args: Array<String>): Int {
    val executor = ProcessBuilderShellCommandExecutor()
    val gradleUserHome = defaultGradleUserHome()
    val fileManager = NioFileManager()
    val userHome = System.getProperty("user.home")
    val derivedDataSource = MacDerivedDataSource(executor, fileManager, userHome)
    val exitCode = ShogunCli(
        executor = executor,
        fileManager = fileManager,
        projectRepository = JsonProjectRepository(),
        provisioningSource = RepositoryProvisioningFiles(),
        daemonSource = ProcessGradleDaemonSource(executor, gradleUserHome),
        gradleUserHome = gradleUserHome,
        environmentStateStore = NioEnvironmentStateStore(),
        portProbe = SocketPortProbe(),
        androidSdk = LocalAndroidSdk(executor, fileManager),
        appleSimulators = SimctlAppleSimulators(executor),
        kmpTestWiring = FileKmpTestWiring(gradleUserHome),
        derivedDataSource = derivedDataSource,
        herdrConfig = HerdrConfigFile(),
        workingDirectory = System.getProperty("user.dir"),
        out = System.out,
        err = System.err,
        readInput = { System.`in`.bufferedReader().readText() },
        trust = JsonRepositoryTrust(),
        readAnswer = { System.console()?.readLine() },
    ).run(args.toList())
    System.out.flush()
    System.err.flush()
    return exitCode
}
