package app.luxion.shogunai.infrastructure

import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostEnvironmentSourceTest {

    private val repositoryRoot: Path = Path.of("").toAbsolutePath().parent
    private val modules = listOf("core", "cli", "desktopApp")
    private val allowed = setOf("SystemHostEnvironment.kt", "FileKmpTestWiring.kt")
    private val reads = listOf("System.getenv", "System::getenv", "System.getProperty(\"user.home\")")

    private fun environmentReads(sources: Path): List<String> =
        sources.walk()
            .filter { it.isRegularFile() && it.extension == "kt" && it.name !in allowed }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> reads.any { it in line } }
                    .map { (index, line) -> "${repositoryRoot.relativize(file)}:${index + 1}: ${line.trim()}" }
            }
            .toList()

    @Test
    fun `only the host environment reads environment variables and the home folder`() {
        val sources = modules.map { repositoryRoot.resolve("$it/src/main/kotlin") }
        assertTrue(sources.all { it.toFile().isDirectory }, "Missing sources under $repositoryRoot")

        assertEquals(emptyList(), sources.flatMap(::environmentReads))
    }
}
