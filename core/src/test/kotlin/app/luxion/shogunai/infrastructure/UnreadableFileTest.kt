package app.luxion.shogunai.infrastructure

import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnreadableFileTest {

    private val directory = createTempDirectory("unreadable-file")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `an unreadable copy gets a numbered name when the stamped one is taken`() {
        val file = directory.resolve("settings.json").also { it.writeText("{") }
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
        listOf("", "-1").forEach { suffix -> Files.writeString(directory.resolve("settings.json.unreadable-$stamp$suffix"), "taken") }

        val kept = keepUnreadableCopy(file)

        assertTrue(kept.fileName.toString().startsWith("settings.json.unreadable-"))
        assertTrue(kept.exists())
        assertEquals("{", Files.readString(kept))
    }
}
