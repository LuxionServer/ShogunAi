package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AppRequest
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileAppRequestChannelTest {

    private val directory = createTempDirectory("app-requests-test").resolve("app-requests")
    private val channel = FileAppRequestChannel(directory)

    @Test
    fun `delivers requests oldest first and consumes them`() {
        val first = AppRequest.openProject(1_000, "project-1", settings = false)
        val second = AppRequest.openProject(2_000, "project-2", settings = true)
        channel.send(second)
        channel.send(first)

        assertEquals(listOf(first, second), channel.receive(nowMillis = 3_000))
        assertTrue(channel.receive(nowMillis = 3_000).isEmpty())
        assertTrue(directory.listDirectoryEntries().isEmpty())
    }

    @Test
    fun `sending leaves no partial file behind`() {
        channel.send(AppRequest.openApp(1_000))

        assertTrue(directory.listDirectoryEntries().all { it.fileName.toString().endsWith(".json") })
    }

    @Test
    fun `discards requests older than a minute`() {
        channel.send(AppRequest.openApp(1_000))
        val fresh = AppRequest.openApp(100_000)
        channel.send(fresh)

        assertEquals(listOf(fresh), channel.receive(nowMillis = 100_500))
        assertTrue(directory.listDirectoryEntries().isEmpty())
    }

    @Test
    fun `deletes malformed files and keeps going`() {
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("0000000000001-garbage.json"), "not json")
        val request = AppRequest.openApp(2_000)
        channel.send(request)

        assertEquals(listOf(request), channel.receive(nowMillis = 3_000))
        assertTrue(directory.listDirectoryEntries().isEmpty())
    }

    @Test
    fun `receives nothing when the folder doesn't exist`() {
        assertTrue(channel.receive(nowMillis = 1_000).isEmpty())
    }
}
