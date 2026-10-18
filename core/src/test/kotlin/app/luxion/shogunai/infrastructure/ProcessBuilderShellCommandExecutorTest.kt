package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomebrewPathTest {

    @Test
    fun `antepone los directorios de Homebrew al PATH heredado`() {
        val merged = HomebrewPath.merge("/usr/bin:/bin")

        assertEquals(
            "/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:/usr/bin:/bin",
            merged,
        )
    }

    @Test
    fun `no duplica un directorio de Homebrew ya presente en el PATH heredado`() {
        val merged = HomebrewPath.merge("/usr/local/bin:/usr/bin")

        assertEquals(1, merged.split(File.pathSeparator).count { it == "/usr/local/bin" })
    }

    @Test
    fun `funciona con un PATH nulo o vacío`() {
        assertEquals("/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin", HomebrewPath.merge(null))
        assertEquals("/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin", HomebrewPath.merge(""))
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
    fun `refleja un código de salida distinto de cero sin lanzar`() = runTest {
        val result = executor.execute(listOf("sh", "-c", "exit 3"))

        assertEquals(3, result.exitCode)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `ejecuta el comando en el directorio de trabajo indicado`() = runTest {
        val cwd = System.getProperty("java.io.tmpdir")

        val result = executor.execute(listOf("pwd"), workingDirectory = cwd)

        assertEquals(File(cwd).canonicalPath, File(result.stdout).canonicalPath)
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
        val result = executor.execute(listOf("sh", "-c", "echo \$PATH"))

        val path = result.stdout.split(File.pathSeparator)
        assertTrue(path.contains("/opt/homebrew/bin"))
        assertTrue(path.contains("/opt/homebrew/sbin"))
        assertTrue(path.contains("/usr/local/bin"))
    }
}
