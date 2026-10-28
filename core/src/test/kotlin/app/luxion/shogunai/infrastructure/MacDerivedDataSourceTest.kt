package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.DerivedDataFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacDerivedDataSourceTest {

    private val home: Path = Files.createTempDirectory("mac-derived-data-home")
    private val defaultLocation: Path = home.resolve("Library/Developer/Xcode/DerivedData")
    private val volumes: Path = home.resolve("Volumes")

    @AfterTest
    fun cleanUp() {
        home.toFile().deleteRecursively()
    }

    @Test
    fun `is supported only on macOS`() {
        assertTrue(dataSource().isSupported())
        assertTrue(dataSource(osName = "macOS").isSupported())
        assertFalse(dataSource(osName = "Linux").isSupported())
        assertFalse(dataSource(osName = "Windows 11").isSupported())
    }

    @Test
    fun `reads the folders of the default location`() = runTest {
        val main = folder("MainRepo-hash").apply {
            writePlist(this, "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj", "2024-12-17T14:01:19Z")
            resolve("Build").createDirectories().resolve("app").writeText("binary")
        }
        val expectedSize = Files.readAllBytes(main.resolve("info.plist")).size.toLong() + "binary".length.toLong()
        val executor = defaultsExecutor()

        val folders = dataSource(executor).folders()

        assertEquals(listOf(main.toString()), folders.map { it.path })
        assertEquals("/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj", folders.single().workspacePath)
        assertEquals(Instant.parse("2024-12-17T14:01:19Z"), folders.single().lastAccessed)
        assertNull(folders.single().sizeBytes)
        assertEquals(expectedSize, dataSource(executor).size(folders.single()))
        assertTrue(executor.executed("defaults", "read", "com.apple.dt.Xcode", "IDEDerivedDataPathOverride"))
        assertTrue(executor.executed("defaults", "read", "com.apple.dt.Xcode", "IDECustomDerivedDataLocation"))
    }

    @Test
    fun `resolves a workspace path recorded through a symbolic link to its real path`() = runTest {
        val project = home.resolve("real/main-repo/App.xcodeproj").createDirectories()
        val link = Files.createSymbolicLink(home.resolve("link"), home.resolve("real"))
        folder("Linked-hash").apply { writePlist(this, link.resolve("main-repo/App.xcodeproj").toString()) }

        val folders = dataSource().folders()

        assertEquals(project.toRealPath().toString(), folders.single().workspacePath)
    }

    @Test
    fun `keeps the recorded workspace path when the project no longer exists`() = runTest {
        val gone = home.resolve("gone/main-repo/App.xcodeproj").toString()
        folder("Gone-hash").apply { writePlist(this, gone) }

        val folders = dataSource().folders()

        assertEquals(gone, folders.single().workspacePath)
    }

    @Test
    fun `reads the custom location from preferences instead of the default`() = runTest {
        val custom = home.resolve("CustomDerivedData").createDirectories()
        custom.resolve("Other-hash").createDirectories().apply { writePlist(this, "/home/dev/other-project/App.xcodeproj") }
        folder("MainRepo-hash").apply { writePlist(this, "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj") }
        val executor = defaultsExecutor("IDECustomDerivedDataLocation" to custom.toString())

        val folders = dataSource(executor).folders()

        assertEquals(listOf(custom.resolve("Other-hash").toString()), folders.map { it.path })
        assertNull(folders.single().lastAccessed)
    }

    @Test
    fun `prefers the derived data override over the custom location`() = runTest {
        val overridden = home.resolve("Override").createDirectories()
        overridden.resolve("MainRepo-hash").createDirectories().apply { writePlist(this, "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj") }
        val executor = defaultsExecutor(
            "IDEDerivedDataPathOverride" to overridden.toString(),
            "IDECustomDerivedDataLocation" to home.resolve("Never").toString(),
        )

        val folders = dataSource(executor).folders()

        assertEquals(listOf(overridden.resolve("MainRepo-hash").toString()), folders.map { it.path })
        assertFalse(executor.executed("defaults", "read", "com.apple.dt.Xcode", "IDECustomDerivedDataLocation"))
    }

    @Test
    fun `expands a leading tilde in the preference`() = runTest {
        val tildeLocation = home.resolve("TildeDerived").createDirectories()
        tildeLocation.resolve("MainRepo-hash").createDirectories().apply { writePlist(this, "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj") }
        val executor = defaultsExecutor("IDECustomDerivedDataLocation" to "~/TildeDerived")

        val folders = dataSource(executor).folders()

        assertEquals(listOf(tildeLocation.resolve("MainRepo-hash").toString()), folders.map { it.path })
    }

    @Test
    fun `skips shared caches without an info plist`() = runTest {
        val main = folder("MainRepo-hash").apply { writePlist(this, "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj") }
        folder("CompilationCache.noindex").resolve("obj.o").writeText("object")
        folder("ModuleCache.noindex").resolve("mod.pcm").writeText("module")

        val folders = dataSource().folders()

        assertEquals(listOf(main.toString()), folders.map { it.path })
    }

    @Test
    fun `skips a folder with a binary info plist`() = runTest {
        val main = folder("MainRepo-hash").apply { writePlist(this, "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj") }
        val binary = folder("Binary-hash")
        binary.resolve("info.plist").writeBytes("bplist00".toByteArray() + byteArrayOf(1, 2, 3))

        val folders = dataSource().folders()

        assertEquals(listOf(main.toString()), folders.map { it.path })
    }

    @Test
    fun `skips folders without a workspace path or an unparseable plist`() = runTest {
        val noWorkspace = folder("NoWorkspace-hash")
        noWorkspace.resolve("info.plist").writeText(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<plist version=\"1.0\">\n<dict>\n\t<key>Something</key>\n\t<string>value</string>\n</dict>\n</plist>\n",
        )
        val garbage = folder("Garbage-hash")
        garbage.resolve("info.plist").writeText("not xml at all")

        val folders = dataSource().folders()

        assertTrue(folders.isEmpty())
    }

    @Test
    fun `returns no folders when the location is missing`() = runTest {
        val source = dataSource(defaultsExecutor("IDECustomDerivedDataLocation" to home.resolve("Absent").toString()))

        val folders = source.folders()

        assertTrue(folders.isEmpty())
    }

    @Test
    fun `stops measuring a folder when its coroutine is cancelled`() = runTest {
        val big = folder("Big-hash").apply {
            writePlist(this, "/home/dev/projects/main-repo/App.xcodeproj")
            repeat(200) { resolve("Build/$it").createDirectories().resolve("file").writeText("x") }
        }
        val source = dataSource()
        val entry = source.folders().single()

        val measuring = async(Dispatchers.IO, start = CoroutineStart.LAZY) { source.size(entry) }
        measuring.cancel()

        assertFailsWith<CancellationException> { measuring.await() }
        assertTrue(big.toFile().exists())
    }

    @Test
    fun `measures the compilation cache of the default location`() = runTest {
        val cache = folder("CompilationCache.noindex")
        cache.resolve("obj.o").writeText("object")

        val bytes = dataSource().compilationCacheBytes()

        assertEquals("object".length.toLong(), bytes)
    }

    @Test
    fun `returns null when the compilation cache directory is missing`() = runTest {
        assertNull(dataSource().compilationCacheBytes())
    }

    @Test
    fun `measures the compilation cache of the custom location`() = runTest {
        val cache = home.resolve("CustomCache").createDirectories()
        cache.resolve("obj.o").writeText("object")
        val executor = defaultsExecutor("IDECustomCompilationCacheLocation" to cache.toString())

        val bytes = dataSource(executor).compilationCacheBytes()

        assertEquals("object".length.toLong(), bytes)
    }

    @Test
    fun `reads the free space of the volume holding the location`() = runTest {
        defaultLocation.createDirectories()

        val free = dataSource().freeBytes()

        assertEquals(Files.getFileStore(defaultLocation).usableSpace, free)
    }

    @Test
    fun `reads the free space of the nearest existing parent when the location is missing`() = runTest {
        val free = dataSource().freeBytes()

        assertEquals(Files.getFileStore(home).usableSpace, free)
    }

    @Test
    fun `detects unavailable volumes under the volumes root`() {
        val source = dataSource()
        volumes.resolve("Mounted").createDirectories()

        assertTrue(source.isOnUnavailableVolume("${volumes}/Unmounted/app/App.xcodeproj"))
        assertFalse(source.isOnUnavailableVolume("${volumes}/Mounted/app/App.xcodeproj"))
        assertFalse(source.isOnUnavailableVolume(volumes.toString()))
        assertFalse(source.isOnUnavailableVolume("/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj"))
    }

    @Test
    fun `deletes a direct child with an info plist`() = runTest {
        val child = folder("MainRepo-hash").apply {
            writePlist(this, "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj")
            resolve("Build").createDirectories().resolve("app").writeText("binary")
        }
        val source = dataSource()

        source.delete(source.folders().single())

        assertFalse(Files.exists(child))
    }

    @Test
    fun `refuses to delete a child without an info plist`() = runTest {
        val child = folder("NoPlist-hash")
        val source = dataSource()

        assertFailsWith<IllegalStateException> {
            source.delete(DerivedDataFolder(child.toString(), "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj", 1L, null))
        }
        assertTrue(Files.exists(child))
    }

    @Test
    fun `refuses to delete a path that is not a direct child`() = runTest {
        val outside = home.resolve("Outside").createDirectories().apply { writePlist(this, "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj") }
        val nested = folder("MainRepo-hash").resolve("Build").createDirectories()
        val source = dataSource()

        assertFailsWith<IllegalStateException> {
            source.delete(DerivedDataFolder(outside.toString(), "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj", 1L, null))
        }
        assertFailsWith<IllegalStateException> {
            source.delete(DerivedDataFolder(nested.toString(), "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj", 1L, null))
        }
        assertTrue(Files.exists(outside))
        assertTrue(Files.exists(nested))
    }

    @Test
    fun `does not follow symbolic links out of the folder`() = runTest {
        val outside = home.resolve("OutsideTarget").createDirectories()
        outside.resolve("keep.txt").writeText("keep")
        val child = folder("Link-hash").apply { writePlist(this, "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj") }
        Files.createSymbolicLink(child.resolve("link-dir"), outside)
        val source = dataSource()

        source.delete(source.folders().single())

        assertFalse(Files.exists(child))
        assertTrue(Files.exists(outside.resolve("keep.txt")))
    }

    private fun dataSource(
        executor: FakeShellCommandExecutor = defaultsExecutor(),
        osName: String = "Mac OS X",
    ): MacDerivedDataSource = MacDerivedDataSource(executor, NioFileManager(), home.toString(), osName = osName, volumesRoot = volumes.toString())

    private fun defaultsExecutor(vararg values: Pair<String, String>): FakeShellCommandExecutor {
        val byKey = values.toMap()
        return FakeShellCommandExecutor { command ->
            val stdout = byKey[command.getOrNull(3)]
            if (command.take(3) == listOf("defaults", "read", "com.apple.dt.Xcode") && stdout != null) {
                FakeShellCommandExecutor.success(command, stdout = stdout)
            } else {
                FakeShellCommandExecutor.failure(command, exitCode = 1, stderr = "does not exist")
            }
        }
    }

    private fun folder(name: String): Path = defaultLocation.resolve(name).createDirectories()

    private fun writePlist(folder: Path, workspacePath: String, lastAccessed: String? = null) {
        val date = lastAccessed?.let { "\t<key>LastAccessedDate</key>\n\t<date>$it</date>\n" }.orEmpty()
        folder.resolve("info.plist").writeText(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                "<plist version=\"1.0\">\n<dict>\n" +
                date +
                "\t<key>WorkspacePath</key>\n\t<string>$workspacePath</string>\n" +
                "</dict>\n</plist>\n",
        )
    }

    @Test
    fun `measures the swift packages of a folder and zero without them`() = runTest {
        val app = folder("App-hash")
        writePlist(app, "/home/dev/projects/app/App.xcodeproj")
        app.resolve("SourcePackages/checkouts/Package").createDirectories().resolve("Package.swift").writeText("12345")
        app.resolve("Build").createDirectories().resolve("app.o").writeText("123")
        val other = folder("Other-hash")
        writePlist(other, "/home/dev/projects/other/Other.xcodeproj")
        val source = dataSource()
        val folders = source.folders().associateBy { Path.of(it.path).fileName.toString() }

        assertEquals(5L, source.packagesSize(folders.getValue("App-hash")))
        assertEquals(8L + Files.size(app.resolve("info.plist")), source.size(folders.getValue("App-hash")))
        assertEquals(0L, source.packagesSize(folders.getValue("Other-hash")))
    }
}
