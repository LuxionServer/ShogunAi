package app.luxion.shogunai.cli

import app.luxion.shogunai.infrastructure.FileKmpTestWiring
import app.luxion.shogunai.infrastructure.HerdrConfigFile
import app.luxion.shogunai.infrastructure.JsonDerivedDataSizeStore
import app.luxion.shogunai.infrastructure.JsonProjectRepository
import app.luxion.shogunai.infrastructure.JsonResourceLimitsRepository
import app.luxion.shogunai.infrastructure.JsonSettingsFile
import app.luxion.shogunai.infrastructure.JsonRepositoryTrust
import app.luxion.shogunai.infrastructure.JvmMachineCapacity
import app.luxion.shogunai.infrastructure.LocalAndroidSdk
import app.luxion.shogunai.infrastructure.MacDerivedDataSource
import app.luxion.shogunai.infrastructure.MacPackageCacheSource
import app.luxion.shogunai.infrastructure.NioEnvironmentStateStore
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor
import app.luxion.shogunai.infrastructure.ProcessGradleDaemonSource
import app.luxion.shogunai.infrastructure.NioProvisioningRecordStore
import app.luxion.shogunai.infrastructure.RepositoryProvisioningFiles
import app.luxion.shogunai.infrastructure.SimctlAppleSimulators
import app.luxion.shogunai.infrastructure.ShogunFiles
import app.luxion.shogunai.infrastructure.SocketPortProbe
import app.luxion.shogunai.infrastructure.SystemHostEnvironment
import app.luxion.shogunai.infrastructure.defaultGradleUserHome
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runShogun(args))
}

fun runShogun(args: Array<String>): Int {
    val environment = SystemHostEnvironment.forCommandLine()
    val files = ShogunFiles(environment.home)
    val executor = ProcessBuilderShellCommandExecutor(path = { environment.path.folders })
    val gradleUserHome = defaultGradleUserHome(environment)
    val fileManager = NioFileManager()
    val userHome = environment.home
    val derivedDataSource = MacDerivedDataSource(executor, fileManager, userHome)
    val exitCode = ShogunCli(
        executor = executor,
        fileManager = fileManager,
        projectRepository = JsonProjectRepository(files.projects),
        provisioningSource = RepositoryProvisioningFiles(),
        daemonSource = ProcessGradleDaemonSource(executor, gradleUserHome),
        gradleUserHome = gradleUserHome,
        environmentStateStore = NioEnvironmentStateStore(files.folder),
        portProbe = SocketPortProbe(),
        androidSdk = LocalAndroidSdk(executor, fileManager, environment::variable, userHome, logDirectory = files.logs),
        appleSimulators = SimctlAppleSimulators(executor),
        kmpTestWiring = FileKmpTestWiring(gradleUserHome),
        resourceLimits = JsonResourceLimitsRepository(JsonSettingsFile(files.settings)),
        machineCapacity = JvmMachineCapacity(),
        derivedDataSource = derivedDataSource,
        herdrConfig = HerdrConfigFile(environment::variable, userHome),
        workingDirectory = System.getProperty("user.dir"),
        out = System.out,
        err = System.err,
        readInput = { System.`in`.bufferedReader().readText() },
        trust = JsonRepositoryTrust(files.trust),
        readAnswer = { System.console()?.readLine() },
        provisioningRecords = NioProvisioningRecordStore(),
        derivedDataSizes = JsonDerivedDataSizeStore(files.derivedDataSizes),
        packageCacheSource = MacPackageCacheSource(fileManager, userHome),
        environment = environment,
        files = files,
    ).run(args.toList())
    System.out.flush()
    System.err.flush()
    return exitCode
}
