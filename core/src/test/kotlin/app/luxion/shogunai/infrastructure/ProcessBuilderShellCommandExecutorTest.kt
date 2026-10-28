package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.OutputChannel
import app.luxion.shogunai.domain.executor.OutputLine
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomebrewPathTest {

    @Test
    fun `antepone los directorios de Homebrew al PATH heredado`() {
        val merged = HomebrewPath.merge(listOf("/usr/bin", "/bin").joinToString(File.pathSeparator))

        assertEquals(
            listOf("/opt/homebrew/bin", "/opt/homebrew/sbin", "/usr/local/bin", "/usr/bin", "/bin").joinToString(File.pathSeparator),
            merged,
        )
    }

    @Test
    fun `no duplica un directorio de Homebrew ya presente en el PATH heredado`() {
        val merged = HomebrewPath.merge(listOf("/usr/local/bin", "/usr/bin").joinToString(File.pathSeparator))

        assertEquals(1, merged.split(File.pathSeparator).count { it == "/usr/local/bin" })
    }

    @Test
    fun `funciona con un PATH nulo o vacío`() {
        val expected = listOf("/opt/homebrew/bin", "/opt/homebrew/sbin", "/usr/local/bin").joinToString(File.pathSeparator)

        assertEquals(expected, HomebrewPath.merge(null))
        assertEquals(expected, HomebrewPath.merge(""))
    }
}

class GitLocaleTest {

    @Test
    fun `fuerza LC_ALL=C en los comandos de git`() {
        val environment = mutableMapOf("LC_ALL" to "es_ES.UTF-8")

        GitLocale.applyTo(listOf("git", "status"), environment)

        assertEquals("C", environment["LC_ALL"])
    }

    @Test
    fun `no toca el entorno de otros comandos`() {
        val environment = mutableMapOf("LC_ALL" to "es_ES.UTF-8")

        GitLocale.applyTo(listOf("adb", "devices"), environment)

        assertEquals("es_ES.UTF-8", environment["LC_ALL"])
    }
}

class GitLongPathsTest {

    @Test
    fun `git commands on Windows turn on long path support`() {
        assertEquals(
            listOf("git", "-c", "core.longpaths=true", "worktree", "remove", "C:\\projects\\api-TASK-123"),
            GitLongPaths.prepare(listOf("git", "worktree", "remove", "C:\\projects\\api-TASK-123"), "Windows 11"),
        )
    }

    @Test
    fun `other programs on Windows are left unchanged`() {
        val command = listOf("adb", "devices")

        assertEquals(command, GitLongPaths.prepare(command, "Windows 11"))
    }

    @Test
    fun `git commands on other systems are left unchanged`() {
        val command = listOf("git", "status", "--porcelain")

        assertEquals(command, GitLongPaths.prepare(command, "Mac OS X"))
        assertEquals(command, GitLongPaths.prepare(command, "Linux"))
    }
}

class ProcessBuilderShellCommandExecutorTest {

    private val executor = ProcessBuilderShellCommandExecutor()

    @Test
    fun `captura stdout, stderr y el código de salida de un comando exitoso`() = runTest {
        val result = executor.execute(listOf("sh", "-c", "echo out; echo err 1>&2"))

        assertEquals("out", result.stdout)
        assertEquals("err", result.stderr)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `fuera de Windows entrega los argumentos con espacios y comillas sin alterarlos`() = runTest {
        val argument = "A=-Dx=\"a b\" -Dy=1\\"

        val result = executor.execute(listOf("printf", "%s", argument))

        assertEquals(argument, result.stdout)
    }

    @Test
    fun `pasa al proceso las variables de entorno indicadas`() = runTest {
        val result = executor.executeWithEnvironment(listOf("sh", "-c", "printf %s \"\$TOUR_VALUE\""), mapOf("TOUR_VALUE" to "a b"))

        assertEquals("a b", result.stdout)
    }

    @Test
    fun `sin entorno indicado no añade variables`() = runTest {
        val result = executor.execute(listOf("sh", "-c", "printf %s \"\${TOUR_VALUE:-unset}\""))

        assertEquals("unset", result.stdout)
    }

    @Test
    fun `refleja un código de salida distinto de cero sin lanzar`() = runTest {
        val result = executor.execute(listOf("sh", "-c", "exit 3"))

        assertEquals(3, result.exitCode)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `ejecuta el comando en el directorio de trabajo indicado`() = runTest {
        val cwd = Files.createTempDirectory("executor-cwd").toFile()

        try {
            executor.execute(listOf("sh", "-c", "echo created > marker.txt"), workingDirectory = cwd.path)

            assertTrue(File(cwd, "marker.txt").exists())
        } finally {
            cwd.deleteRecursively()
        }
    }

    @Test
    fun `lanza CommandExecutionException si el ejecutable no existe`() = runTest {
        assertFailsWith<CommandExecutionException> {
            executor.execute(listOf("comando-que-no-existe-en-ningun-path"))
        }
    }

    @Test
    fun `un proceso git recibe LC_ALL=C`() = runTest {
        val result = executor.execute(listOf("git", "-c", "alias.show-locale=!echo \$LC_ALL", "show-locale"))

        assertEquals("C", result.stdout)
    }

    @Test
    fun `el proceso lanzado ve el PATH ampliado con los directorios de Homebrew`() = runTest {
        if (System.getProperty("os.name").startsWith("Windows")) return@runTest
        val result = executor.execute(listOf("sh", "-c", "echo \$PATH"))

        val path = result.stdout.split(":")
        assertTrue(path.contains("/opt/homebrew/bin"))
        assertTrue(path.contains("/opt/homebrew/sbin"))
        assertTrue(path.contains("/usr/local/bin"))
    }

    @Test
    fun `un comando que lee de la entrada estándar recibe fin de fichero`() = runTest {
        val result = executor.execute(listOf("cat"))

        assertTrue(result.isSuccess)
        assertEquals("", result.stdout)
    }

    @Test
    fun `cancelar la operación mata el comando y sus hijos en pocos segundos`() = runBlocking {
        val marker = "98765${System.nanoTime() % 1000}"
        val job = launch(Dispatchers.Default) { executor.execute(listOf("sh", "-c", "sleep $marker; true")) }
        awaitProcess(marker, running = true)

        val elapsed = measureTimeMillis { job.cancelAndJoin() }

        assertTrue(elapsed < 5_000, "cancelling took $elapsed ms")
        awaitProcess(marker, running = false)
        assertFalse(isRunning(marker))
    }

    @Test
    fun `un comando que supera el límite lanza TimedOut y no deja procesos`() = runBlocking {
        val marker = "87654${System.nanoTime() % 1000}"

        val error = assertFailsWith<CommandExecutionException.TimedOut> {
            executor.executeWithin(listOf("sh", "-c", "sleep $marker; true"), 500.milliseconds)
        }

        assertEquals(500.milliseconds, error.limit)
        awaitProcess(marker, running = false)
        assertFalse(isRunning(marker))
    }

    @Test
    fun `un comando dentro del límite devuelve su resultado`() = runTest {
        val result = executor.executeWithin(listOf("echo", "ok"), 10.seconds)

        assertEquals("ok", result.stdout)
    }

    @Test
    fun `la ejecución con salida en vivo añade el entorno y entrega cada línea con su canal`() = runTest {
        val lines = mutableListOf<OutputLine>()

        val result = executor.executeStreaming(
            listOf("sh", "-c", "echo \"port=${'$'}DB_PORT\"; echo second; echo warn 1>&2"),
            workingDirectory = null,
            environment = mapOf("DB_PORT" to "20004"),
        ) { lines += it }

        assertTrue(result.isSuccess)
        assertEquals("port=20004\nsecond", result.stdout)
        assertEquals("warn", result.stderr)
        assertEquals(
            listOf(OutputLine(OutputChannel.STANDARD, "port=20004"), OutputLine(OutputChannel.STANDARD, "second")),
            lines.filter { it.channel == OutputChannel.STANDARD },
        )
        assertEquals(listOf(OutputLine(OutputChannel.ERROR, "warn")), lines.filter { it.channel == OutputChannel.ERROR })
    }

    @Test
    fun `la ejecución con salida en vivo conserva el PATH ampliado`() = runTest {
        if (System.getProperty("os.name").startsWith("Windows")) return@runTest
        val result = executor.executeStreaming(listOf("sh", "-c", "echo ${'$'}PATH"), null, mapOf("OTHER" to "1")) {}

        assertTrue(result.stdout.split(":").contains("/opt/homebrew/bin"))
    }

    private fun isRunning(marker: String): Boolean = ProcessHandle.allProcesses().anyMatch { handle ->
        val info = handle.info()
        (info.arguments().map { it.joinToString(" ") }.orElse("") + info.commandLine().orElse("")).contains(marker)
    }

    private suspend fun awaitProcess(marker: String, running: Boolean) {
        repeat(100) {
            if (isRunning(marker) == running) return
            delay(50)
        }
    }
}
