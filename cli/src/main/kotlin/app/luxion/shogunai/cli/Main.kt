package app.luxion.shogunai.cli

import app.luxion.shogunai.engine.ShogunEngine
import app.luxion.shogunai.infrastructure.JsonSettingsFile
import app.luxion.shogunai.infrastructure.ShogunFiles
import app.luxion.shogunai.infrastructure.SystemHostEnvironment
import app.luxion.shogunai.infrastructure.hostEnginePorts
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runShogun(args))
}

fun runShogun(args: Array<String>): Int {
    val environment = SystemHostEnvironment.forCommandLine()
    val files = ShogunFiles(environment.home)
    val workingDirectory = System.getProperty("user.dir")
    val ports = hostEnginePorts(
        environment,
        files,
        JsonSettingsFile(files.settings),
        sdkRepository = { workingDirectory },
        shareDeviceStates = false,
    )
    val out = utf8Stream(FileDescriptor.out)
    val err = utf8Stream(FileDescriptor.err)
    val exitCode = ShogunCli(
        engine = ShogunEngine(ports),
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        readInput = { System.`in`.bufferedReader().readText() },
        readAnswer = { System.console()?.readLine() },
        environment = environment,
        files = files,
    ).run(args.toList())
    out.flush()
    err.flush()
    return exitCode
}

private fun utf8Stream(descriptor: FileDescriptor): PrintStream =
    PrintStream(FileOutputStream(descriptor), true, Charsets.UTF_8)
