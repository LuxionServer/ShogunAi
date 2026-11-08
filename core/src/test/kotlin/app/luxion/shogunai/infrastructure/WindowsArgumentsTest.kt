package app.luxion.shogunai.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsArgumentsTest {

    private val gradleOpts =
        "GRADLE_OPTS=-Dorg.gradle.jvmargs=\"-Xmx4g -Dfile.encoding=UTF-8\" " +
            "-Dorg.gradle.daemon.idletimeout=1800000 -Dorg.gradle.project.kotlin.daemon.jvmargs=-Xmx3g"

    private val samples = listOf(
        "plain",
        "--focus",
        "C:\\dir\\sub",
        "C:\\dir\\",
        "with space",
        "with\ttab",
        "C:\\Program Files\\My App\\",
        "C:\\Program Files\\My App\\\\",
        "say \"hi\"",
        "a\\\"b",
        "a\\\\\"b c",
        "\"",
        "\"\"",
        "\"quoted\"",
        "trailing\\",
        "",
        "=x y=",
        gradleOpts,
    )

    @Test
    fun `no toca un argumento sin espacios, tabuladores ni comillas`() {
        assertEquals("plain", WindowsArguments.quote("plain"))
        assertEquals("C:\\dir\\", WindowsArguments.quote("C:\\dir\\"))
        assertEquals("--env=A=b", WindowsArguments.quote("--env=A=b"))
    }

    @Test
    fun `entrecomilla un argumento con espacios o tabuladores`() {
        assertEquals("\"with space\"", WindowsArguments.quote("with space"))
        assertEquals("\"with\ttab\"", WindowsArguments.quote("with\ttab"))
    }

    @Test
    fun `entrecomilla el argumento vacío`() {
        assertEquals("\"\"", WindowsArguments.quote(""))
    }

    @Test
    fun `escapa las comillas incrustadas`() {
        assertEquals("\"say \\\"hi\\\"\"", WindowsArguments.quote("say \"hi\""))
        assertEquals("\"\\\"\"", WindowsArguments.quote("\""))
    }

    @Test
    fun `duplica las barras invertidas que preceden a una comilla`() {
        assertEquals("\"a\\\\\\\"b\"", WindowsArguments.quote("a\\\"b"))
        assertEquals("\"a\\\\\\\\\\\"b c\"", WindowsArguments.quote("a\\\\\"b c"))
    }

    @Test
    fun `duplica las barras invertidas finales cuando entrecomilla`() {
        assertEquals("\"C:\\Program Files\\My App\\\\\"", WindowsArguments.quote("C:\\Program Files\\My App\\"))
        assertEquals("\"C:\\a b\\\\\\\\\"", WindowsArguments.quote("C:\\a b\\\\"))
    }

    @Test
    fun `el valor de GRADLE_OPTS del informe queda como un único argumento`() {
        assertEquals(
            "\"GRADLE_OPTS=-Dorg.gradle.jvmargs=\\\"-Xmx4g -Dfile.encoding=UTF-8\\\" " +
                "-Dorg.gradle.daemon.idletimeout=1800000 -Dorg.gradle.project.kotlin.daemon.jvmargs=-Xmx3g\"",
            WindowsArguments.quote(gradleOpts),
        )
    }

    @Test
    fun `la línea de comandos resultante se interpreta de vuelta como los argumentos originales`() {
        val command = listOf("herdr.exe", "pane", "split", "--env", gradleOpts, "--cwd") + samples

        val prepared = WindowsArguments.prepare(command, "Windows 11")

        assertEquals(command.drop(1), parseCommandLine(jdkCommandLine(prepared)).drop(1))
    }

    @Test
    fun `no toca el ejecutable`() {
        val prepared = WindowsArguments.prepare(listOf("C:\\Program Files\\tool.exe", "a b"), "Windows 11")

        assertEquals(listOf("C:\\Program Files\\tool.exe", "\"a b\""), prepared)
    }

    @Test
    fun `no toca los comandos cuyo ejecutable es un cmd o bat`() {
        listOf("studio.cmd", "C:\\tools\\Code.CMD", "run.bat", "RUN.BAT").forEach { launcher ->
            val command = listOf(launcher, "C:\\my dir\\file.txt", "a \"b\"")

            assertEquals(command, WindowsArguments.prepare(command, "Windows 11"))
        }
    }

    @Test
    fun `no toca los comandos fuera de Windows`() {
        val command = listOf("git", "commit", "-m", "say \"hi\" there", "")

        assertEquals(command, WindowsArguments.prepare(command, "Mac OS X"))
        assertEquals(command, WindowsArguments.prepare(command, "Linux"))
    }

    private fun jdkCommandLine(command: List<String>): String =
        command.mapIndexed { index, argument ->
            val alreadyQuoted = argument.length >= 2 && argument.startsWith('"') && argument.endsWith('"')
            val wrapsItself = index > 0 && !alreadyQuoted && (argument.isEmpty() || argument.any { it.isWhitespace() })
            if (wrapsItself) "\"$argument\"" else argument
        }.joinToString(" ")

    private fun parseCommandLine(line: String): List<String> {
        val arguments = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var started = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '\\' -> {
                    var count = 0
                    while (index < line.length && line[index] == '\\') {
                        count++
                        index++
                    }
                    if (index < line.length && line[index] == '"') {
                        current.append("\\".repeat(count / 2))
                        if (count % 2 == 1) {
                            current.append('"')
                            index++
                        }
                    } else {
                        current.append("\\".repeat(count))
                    }
                    started = true
                    continue
                }
                char == '"' -> {
                    inQuotes = !inQuotes
                    started = true
                }
                (char == ' ' || char == '\t') && !inQuotes -> {
                    if (started) arguments += current.toString()
                    current.clear()
                    started = false
                }
                else -> {
                    current.append(char)
                    started = true
                }
            }
            index++
        }
        if (started) arguments += current.toString()
        return arguments
    }

    @Test
    fun `only Windows commands with arguments that aren't batch files are quoted`() {
        val command = listOf("tool", "a b")

        assertEquals(command, WindowsArguments.prepare(command, "Mac OS X"))
        assertEquals(listOf("tool"), WindowsArguments.prepare(listOf("tool"), "Windows 11"))
        assertEquals(listOf("run.cmd", "a b"), WindowsArguments.prepare(listOf("run.cmd", "a b"), "Windows 11"))
        assertEquals(listOf("tool", "\"a b\""), WindowsArguments.prepare(command, "Windows 11"))
    }
}
