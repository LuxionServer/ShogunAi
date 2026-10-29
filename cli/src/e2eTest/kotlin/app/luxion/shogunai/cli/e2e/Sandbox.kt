package app.luxion.shogunai.cli.e2e

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

data class ProcessResult(val command: List<String>, val exitCode: Int, val stdout: String, val stderr: String) {
    val output: String get() = stdout.trim()

    fun describe(): String = "$command exited with $exitCode\nstdout:\n$stdout\nstderr:\n$stderr"
}

class Sandbox : AutoCloseable {

    val root: Path = Files.createTempDirectory("shogun-e2e").toRealPath()
    val home: Path = root.resolve("home").createDirectories()
    val pathFolder: Path = root.resolve("path").createDirectories()

    init {
        home.resolve(".gitconfig").writeText(
            """
            [user]
            	name = Test User
            	email = test@example.com
            [init]
            	defaultBranch = main
            [core]
            	autocrlf = false
            [commit]
            	gpgsign = false
            """.trimIndent() + "\n",
        )
    }

    fun shogun(directory: Path, vararg arguments: String): ProcessResult =
        run(listOf(launcher().toString()) + arguments, directory)

    fun shogunSucceeds(directory: Path, vararg arguments: String): ProcessResult =
        shogun(directory, *arguments).also { check(it.exitCode == 0) { it.describe() } }

    fun git(directory: Path, vararg arguments: String): String {
        val result = run(listOf("git") + arguments, directory)
        check(result.exitCode == 0) { result.describe() }
        return result.output
    }

    fun repository(name: String, trackedFiles: Map<String, String> = mapOf("README.md" to "$name\n")): Path {
        val origin = root.resolve("remotes/$name.git").createDirectories()
        git(origin, "init", "--quiet", "--bare")
        val repository = root.resolve(name).createDirectories()
        git(repository, "init", "--quiet")
        trackedFiles.forEach { (path, content) -> write(repository, path, content) }
        git(repository, "add", "--all")
        git(repository, "commit", "--quiet", "-m", "Initial commit")
        git(repository, "remote", "add", "origin", origin.toString())
        git(repository, "push", "--quiet", "--set-upstream", "origin", "main")
        return repository
    }

    fun commit(directory: Path, path: String, content: String, message: String) {
        write(directory, path, content)
        git(directory, "add", path)
        git(directory, "commit", "--quiet", "-m", message)
    }

    fun merge(repository: Path, branch: String) {
        git(repository, "merge", "--quiet", "--no-ff", "-m", "Merge $branch", branch)
    }

    fun write(directory: Path, path: String, content: String): Path =
        directory.resolve(path).also { file ->
            file.parent.createDirectories()
            file.writeText(content)
        }

    fun run(command: List<String>, directory: Path, timeoutSeconds: Long = PROCESS_TIMEOUT_SECONDS): ProcessResult {
        val stdout = root.resolve("process-${System.nanoTime()}.out")
        val stderr = root.resolve("process-${System.nanoTime()}.err")
        val process = ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
            .also { builder -> builder.environment().apply { clear(); putAll(environment()) } }
            .start()
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
            error("$command timed out after $timeoutSeconds seconds\nstdout:\n${read(stdout)}\nstderr:\n${read(stderr)}")
        }
        return ProcessResult(command, process.exitValue(), read(stdout), read(stderr)).also {
            Files.deleteIfExists(stdout)
            Files.deleteIfExists(stderr)
        }
    }

    fun environment(): Map<String, String> {
        val inherited = System.getenv().filterKeys { name -> ISOLATED_PREFIXES.none { name.uppercase().startsWith(it) } }
        val path = (listOf(pathFolder.toString()) + inherited[pathVariable(inherited)].orEmpty()).joinToString(File.pathSeparator)
        return inherited - pathVariable(inherited) + mapOf(
            "PATH" to path,
            "HOME" to home.toString(),
            "USERPROFILE" to home.toString(),
            "XDG_CONFIG_HOME" to home.resolve(".config").toString(),
            "GIT_CONFIG_GLOBAL" to home.resolve(".gitconfig").toString(),
            "GIT_CONFIG_NOSYSTEM" to "1",
            "JAVA_HOME" to System.getProperty("java.home"),
            "SHOGUN_OPTS" to "-Duser.home=$home",
        )
    }

    override fun close() {
        root.toFile().walkBottomUp().forEach { file ->
            file.setWritable(true)
            file.delete()
        }
    }

    private fun pathVariable(environment: Map<String, String>): String =
        environment.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"

    private fun read(file: Path): String = if (Files.exists(file)) String(Files.readAllBytes(file), processOutputCharset) else ""

    private fun nullDevice(): File = File(if (isWindows) "NUL" else "/dev/null")

    companion object {
        const val PROCESS_TIMEOUT_SECONDS = 90L

        val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

        val isLinux: Boolean = System.getProperty("os.name").startsWith("Linux", ignoreCase = true)

        private val processOutputCharset: Charset = Charset.forName(System.getProperty("native.encoding"))

        private val ISOLATED_PREFIXES = listOf(
            "HERDR_", "ORCA_", "CLAUDE", "SHOGUNAI_", "GIT_", "JAVA_OPTS", "SHOGUN_OPTS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS",
            "XDG_", "TMUX", "ZELLIJ", "IOS_SIMULATOR_UDID", "ANDROID_SERIAL", "GRADLE_USER_HOME",
        )

        fun launcher(): Path {
            val installation = Path.of(System.getProperty("shogun.e2e.installation") ?: error("shogun.e2e.installation is not set"))
            return installation.resolve("bin").resolve(if (isWindows) "shogun.bat" else "shogun")
        }
    }
}
