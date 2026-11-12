package app.luxion.shogunai.domain

import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CancellationSourceTest {

    private val moduleRoot: Path = Path.of("").toAbsolutePath()
    private val sources: Path = moduleRoot.resolve("src/main/kotlin")

    private val suspending = Regex("""\bsuspend\b""")
    private val bareRunCatching = Regex("""(?<![\w.])runCatching\b""")
    private val broadCatch = Regex("""\bcatch \((\w+): (Exception|Throwable)\)""")
    private val cancellationCatch = Regex("""\bcatch \(\w+: (\w+\.)*CancellationException\)""")
    private val tryStart = Regex("""\btry\b""")

    @Test
    fun `suspending code never swallows a cancellation`() {
        assertTrue(sources.toFile().isDirectory, "Missing sources under $moduleRoot")

        val offenders = sources.walk()
            .filter { it.isRegularFile() && it.extension == "kt" }
            .flatMap(::offendersIn)
            .toList()

        assertEquals(emptyList(), offenders)
    }

    @Test
    fun `a broad catch is accepted after a cancellation catch of the same try or when it rethrows`() {
        val afterCancellation = listOf(
            "try {",
            "    work()",
            "} catch (error: CancellationException) {",
            "    throw error",
            "} catch (error: Exception) {",
            "    record(error)",
            "}",
        )
        val rethrowing = listOf("try {", "    work()", "} catch (error: Throwable) {", "    cleanUp()", "    throw error", "}")
        val swallowing = listOf("try {", "    work()", "} catch (error: Exception) {", "    record(error)", "}", "throw error")
        val wrapping = listOf("try {", "    work()", "} catch (error: Exception) {", "    throw Failed(error)", "}")

        assertTrue(isGuarded(afterCancellation, 4))
        assertTrue(isGuarded(rethrowing, 2))
        assertTrue(!isGuarded(swallowing, 2))
        assertTrue(!isGuarded(wrapping, 2))
    }

    private fun offendersIn(file: Path): Sequence<String> {
        val lines = file.readLines()
        if (lines.none { suspending.containsMatchIn(it) }) return emptySequence()
        return lines.indices.asSequence()
            .filter { index ->
                bareRunCatching.containsMatchIn(lines[index]) ||
                    (broadCatch.containsMatchIn(lines[index]) && !isGuarded(lines, index))
            }
            .map { index -> "${moduleRoot.relativize(file)}:${index + 1}: ${lines[index].trim()}" }
    }

    private fun isGuarded(lines: List<String>, catchIndex: Int): Boolean =
        followsCancellationCatch(lines, catchIndex) || rethrows(lines, catchIndex)

    private fun rethrows(lines: List<String>, catchIndex: Int): Boolean {
        val name = broadCatch.find(lines[catchIndex])!!.groupValues[1]
        val indentation = indentationOf(lines[catchIndex])
        return lines.drop(catchIndex + 1)
            .takeWhile { it.isBlank() || indentationOf(it) > indentation }
            .any { it.trim() == "throw $name" }
    }

    private fun indentationOf(line: String): Int = line.length - line.trimStart().length

    private fun followsCancellationCatch(lines: List<String>, catchIndex: Int): Boolean {
        for (index in catchIndex - 1 downTo 0) {
            val line = lines[index]
            if (cancellationCatch.containsMatchIn(line)) return true
            if (tryStart.containsMatchIn(line)) return false
        }
        return false
    }
}
