package app.luxion.shogunai.e2e

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.fail

class E2eEnvironment private constructor(val home: File, private val bin: File, val workspace: File) {

    val shogunaiFolder: File get() = home.resolve(".shogunai")

    val claudeSettings: File get() = home.resolve(".claude").resolve("settings.json")

    fun repository(name: String, files: Map<String, String> = emptyMap(), ignored: Map<String, String> = emptyMap()): File {
        val repository = workspace.resolve(name).apply { mkdirs() }
        git(repository, "init", "-b", MAIN_BRANCH)
        write(repository, "README.md", "# $name\n")
        files.forEach { (path, text) -> write(repository, path, text) }
        if (ignored.isNotEmpty()) write(repository, ".gitignore", ignored.keys.joinToString("\n", postfix = "\n"))
        git(repository, "add", "-A")
        git(repository, "commit", "-m", "Start $name")
        ignored.forEach { (path, text) -> write(repository, path, text) }
        return repository
    }

    fun installFakeShogun(): File {
        val launcher = bin.resolve(if (isWindows) "shogun.cmd" else "shogun")
        launcher.writeText(if (isWindows) "@echo off\r\n" else "#!/bin/sh\n")
        launcher.setExecutable(true)
        return launcher
    }

    fun git(directory: File, vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git") + arguments)
            .directory(directory)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            fail("git ${arguments.joinToString(" ")} timed out in $directory")
        }
        if (process.exitValue() != 0) fail("git ${arguments.joinToString(" ")} failed in $directory:\n$output")
        return output.trim()
    }

    private fun write(directory: File, path: String, text: String) {
        directory.resolve(path).apply { parentFile.mkdirs() }.writeText(text)
    }

    companion object {
        const val MAIN_BRANCH = "main"
        private const val GIT_TIMEOUT_SECONDS = 30L
        private const val TASK = "./gradlew :desktopApp:desktopE2eTest"
        private val isWindows = System.getProperty("os.name").startsWith("Windows")

        fun prepare(): E2eEnvironment {
            val isolatedHome = System.getProperty("shogunai.e2e.home")
                ?: fail("End-to-end tests run only in their isolated environment: $TASK")
            val home = File(isolatedHome)
            if (File(System.getProperty("user.home")).canonicalFile != home.canonicalFile) {
                fail("user.home isn't the isolated home $isolatedHome: run $TASK")
            }
            val bin = File(System.getProperty("shogunai.e2e.bin"))
            listOf(home, bin).forEach { folder ->
                folder.deleteRecursively()
                folder.mkdirs()
            }
            home.resolve(".gitconfig").writeText(
                """
                [user]
                	name = ShogunAi Test
                	email = test@example.com
                [init]
                	defaultBranch = $MAIN_BRANCH
                [commit]
                	gpgsign = false
                """.trimIndent() + "\n",
            )
            val workspace = Files.createTempDirectory("e2e-").toFile().canonicalFile
            return E2eEnvironment(home, bin, workspace)
        }
    }
}
