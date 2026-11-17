package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.io.DyldCacheSource
import app.luxion.shogunai.domain.model.DyldCacheFolder
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacDyldCacheSourceTest {

    private val root: Path = Files.createTempDirectory("dyld-caches")
    private val executor = FakeShellCommandExecutor { command -> FakeShellCommandExecutor.success(command, "26B100\n") }

    @AfterTest
    fun cleanUp() {
        root.toFile().setReadable(true)
        root.toFile().walkBottomUp().forEach { it.setReadable(true); it.setExecutable(true) }
        root.toFile().deleteRecursively()
    }

    private fun source(osName: String = "Mac OS X", at: Path = root) = MacDyldCacheSource(executor, osName, at.toString())

    @Test
    fun `measures each macOS build folder`() = runTest {
        root.resolve("26B100/runtime-a").createDirectories().resolve("cache").writeBytes(ByteArray(300))
        root.resolve("26A434/runtime-b").createDirectories().resolve("cache").writeBytes(ByteArray(1200))
        Files.writeString(root.resolve("stray-file"), "x")

        assertEquals(
            listOf(
                DyldCacheFolder("26A434", root.resolve("26A434").toString(), 1200),
                DyldCacheFolder("26B100", root.resolve("26B100").toString(), 300),
            ),
            source().folders(),
        )
    }

    @Test
    fun `an unreadable folder has an unknown size`() = runTest {
        assumeTrue(root.fileSystem.supportedFileAttributeViews().contains("posix"))
        val locked = root.resolve("26A434").createDirectories()
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"))
        assumeTrue(!Files.isReadable(locked))

        assertNull(source().folders().single().sizeBytes)
    }

    @Test
    fun `a missing caches folder lists nothing`() = runTest {
        assertEquals(emptyList(), source(at = root.resolve("missing")).folders())
    }

    @Test
    fun `reads the running macOS build`() = runTest {
        assertEquals("26B100", source().currentBuild())
        assertTrue(executor.executed("sw_vers", "-buildVersion"))
    }

    @Test
    fun `an unreadable build is unknown`() = runTest {
        val failing = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it) }
        val blank = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "\n") }

        assertNull(MacDyldCacheSource(failing, "Mac OS X", root.toString()).currentBuild())
        assertNull(MacDyldCacheSource(blank, "Mac OS X", root.toString()).currentBuild())
    }

    @Test
    fun `reads nothing and runs nothing outside macOS`() = runTest {
        root.resolve("26A434").createDirectories()

        assertEquals(emptyList(), source(osName = "Linux").folders())
        assertNull(source(osName = "Linux").currentBuild())
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `none reads nothing`() = runTest {
        assertEquals(emptyList(), DyldCacheSource.NONE.folders())
        assertNull(DyldCacheSource.NONE.currentBuild())
    }
}
