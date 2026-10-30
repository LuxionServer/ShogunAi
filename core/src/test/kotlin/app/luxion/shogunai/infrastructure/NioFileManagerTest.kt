package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.DirectoryListing
import app.luxion.shogunai.domain.model.UnavailableReason
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import app.luxion.shogunai.domain.io.SymbolicLinksNotSupported
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.attribute.PosixFilePermissions
import org.junit.Assume.assumeTrue
import kotlin.test.assertFailsWith
import kotlin.test.AfterTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NioFileManagerTest {

    private val root = createTempDirectory("nio-file-manager-test")
    private val fileManager = NioFileManager()

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `writes text creating folders and replacing the previous contents`() {
        val file = root.resolve("worktree/config/.env.shogunai")

        fileManager.writeText(file.toString(), "PORT=20000\n")
        fileManager.writeText(file.toString(), "PORT=20001\n")

        assertEquals("PORT=20001\n", Files.readString(file))
        assertEquals(listOf(".env.shogunai"), Files.list(file.parent).use { stream -> stream.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun `deletes a folder with its contents and ignores a missing one`() {
        val folder = root.resolve("leftover")
        folder.resolve("a/b").createDirectories()
        folder.resolve("a/b/file.txt").writeBytes("x".toByteArray())

        fileManager.deleteRecursively(folder.toString())
        fileManager.deleteRecursively(folder.toString())

        assertFalse(Files.exists(folder))
    }

    @Test
    fun `deletes read-only files like the objects Git writes`() {
        val folder = root.resolve("repository")
        val objectFile = folder.resolve(".git/objects/ab/cdef")
        objectFile.parent.createDirectories()
        objectFile.writeBytes("x".toByteArray())
        objectFile.toFile().setReadOnly()

        fileManager.deleteRecursively(folder.toString())

        assertFalse(Files.exists(folder))
    }

    @Test
    fun `retries a delete that a process still holds until it lets go`() {
        if (System.getProperty("os.name").startsWith("Windows")) return
        val folder = root.resolve("sample-repo")
        val held = folder.resolve("held").createDirectories()
        held.resolve("index.lock").writeBytes("x".toByteArray())
        held.toFile().setWritable(false)
        var pauses = 0
        val retrying = NioFileManager(deleteAttempts = 3, pauseBeforeRetry = {
            pauses++
            held.toFile().setWritable(true)
        })

        retrying.deleteRecursively(folder.toString())

        assertFalse(Files.exists(folder))
        assertEquals(1, pauses)
    }

    @Test
    fun `gives up after the last attempt with the file system's error`() {
        if (System.getProperty("os.name").startsWith("Windows")) return
        val held = root.resolve("sample-repo/held").createDirectories()
        held.resolve("index.lock").writeBytes("x".toByteArray())
        held.toFile().setWritable(false)
        var pauses = 0

        try {
            assertFailsWith<FileSystemException> { NioFileManager(deleteAttempts = 3, pauseBeforeRetry = { pauses++ }).deleteRecursively(root.resolve("sample-repo").toString()) }
            assertEquals(2, pauses)
        } finally {
            held.toFile().setWritable(true)
        }
    }

    @Test
    fun `reads properties and treats a missing file as empty`() {
        val file = root.resolve("gradle.properties")
        file.writeBytes("org.gradle.caching=true\n# comment\norg.gradle.jvmargs=-Xmx2g\n".toByteArray())

        assertEquals(
            mapOf("org.gradle.caching" to "true", "org.gradle.jvmargs" to "-Xmx2g"),
            fileManager.readProperties(file.toString()),
        )
        assertEquals(emptyMap(), fileManager.readProperties(root.resolve("missing.properties").toString()))
    }

    @Test
    fun `copy creates the missing parent folders of the destination`() {
        val source = root.resolve("repo/.idea/runConfigurations").createDirectories().resolve("App.xml")
        source.writeBytes(ByteArray(10))
        val destination = root.resolve("worktree/.idea/runConfigurations/App.xml")

        fileManager.copy(source.toString(), destination.toString())

        assertEquals(10L, Files.size(destination))
    }

    @Test
    fun `adds up regular files, skipping excluded folders and not following symlinks`() {
        val repo = root.resolve("repo").createDirectories()
        repo.resolve("a.txt").writeBytes(ByteArray(100))
        repo.resolve("src").createDirectories().resolve("b.txt").writeBytes(ByteArray(50))
        val nested = repo.resolve(".claude/worktrees/task").createDirectories()
        nested.resolve("c.txt").writeBytes(ByteArray(1000))
        val outside = root.resolve("outside").createDirectories()
        outside.resolve("d.txt").writeBytes(ByteArray(5000))
        Files.createSymbolicLink(repo.resolve("link"), outside)

        val size = fileManager.directorySize(repo.toString(), excluding = setOf(nested.toString()))

        assertEquals(150L, size)
    }

    @Test
    fun `modification stamps cover nested entries, a single file and a missing path`() {
        val refs = root.resolve("refs").createDirectories()
        refs.resolve("heads/feature").createDirectories().resolve("TASK-1").writeBytes(ByteArray(1))
        val head = root.resolve("HEAD")
        head.writeBytes(ByteArray(1))

        val refsStamps = fileManager.modificationStamps(refs.toString()).map { it.first }.toSet()

        assertEquals(
            setOf(refs, refs.resolve("heads"), refs.resolve("heads/feature"), refs.resolve("heads/feature/TASK-1"))
                .map { it.toString() }.toSet(),
            refsStamps,
        )
        assertEquals(listOf(head.toString()), fileManager.modificationStamps(head.toString()).map { it.first })
        assertEquals(emptyList(), fileManager.modificationStamps(root.resolve("missing").toString()))
    }

    @Test
    fun `creates a symbolic link replacing an existing file and creating folders`() {
        val target = root.resolve("base/.env")
        target.parent.createDirectories()
        Files.writeString(target, "KEY=1")
        val link = root.resolve("worktree/config/.env")
        link.parent.createDirectories()
        Files.writeString(link, "OLD")

        fileManager.createSymbolicLink(target.toString(), link.toString())

        assertTrue(Files.isSymbolicLink(link))
        assertEquals("KEY=1", Files.readString(link))
        assertTrue(fileManager.isSymbolicLinkTo(link.toString(), target.toString()))
    }

    @Test
    fun `a regular file or a link to another file isn't a symbolic link to the target`() {
        val target = root.resolve("base.env")
        val other = root.resolve("other.env")
        val regular = root.resolve("regular.env")
        val linkToOther = root.resolve("link.env")
        listOf(target, other, regular).forEach { Files.writeString(it, "KEY=1") }
        Files.createSymbolicLink(linkToOther, other)

        assertFalse(fileManager.isSymbolicLinkTo(regular.toString(), target.toString()))
        assertFalse(fileManager.isSymbolicLinkTo(linkToOther.toString(), target.toString()))
        assertFalse(fileManager.isSymbolicLinkTo(root.resolve("missing").toString(), target.toString()))
    }

    @Test
    fun `compares the content of two files`() {
        val first = root.resolve("first")
        val second = root.resolve("second")
        val third = root.resolve("third")
        Files.writeString(first, "KEY=1")
        Files.writeString(second, "KEY=1")
        Files.writeString(third, "KEY=2")

        assertTrue(fileManager.hasSameContent(first.toString(), second.toString()))
        assertFalse(fileManager.hasSameContent(first.toString(), third.toString()))
        assertFalse(fileManager.hasSameContent(first.toString(), root.resolve("missing").toString()))
    }

    @Test
    fun `a path under a symlinked folder pointing outside is not inside the root`() {
        val worktree = root.resolve("worktree").also { it.createDirectories() }
        val outside = root.resolve("outside").also { it.createDirectories() }
        Files.createSymbolicLink(worktree.resolve("config"), outside)

        assertFalse(fileManager.isInside(worktree.toString(), worktree.resolve("config/.env").toString()))
        assertFalse(fileManager.isInside(worktree.toString(), worktree.resolve("config/new/.env").toString()))
        assertTrue(outside.toFile().list().isNullOrEmpty())
    }

    @Test
    fun `a regular or missing folder inside the root is inside it`() {
        val worktree = root.resolve("worktree").also { it.createDirectories() }
        worktree.resolve("config").createDirectories()

        assertTrue(fileManager.isInside(worktree.toString(), worktree.resolve("config/.env").toString()))
        assertTrue(fileManager.isInside(worktree.toString(), worktree.resolve("missing/deeper/.env").toString()))
    }

    @Test
    fun `lists nested and hidden regular files with relative paths`() {
        val keys = root.resolve("repo/keys").createDirectories()
        keys.resolve("a.pem").writeBytes(ByteArray(1))
        keys.resolve(".hidden").writeBytes(ByteArray(1))
        keys.resolve("ci/deep").createDirectories().resolve("b.pem").writeBytes(ByteArray(1))

        assertEquals(
            listOf(".hidden", "a.pem", "ci/deep/b.pem"),
            fileManager.listTree(keys.toString()).files.sorted(),
        )
    }

    @Test
    fun `listing skips symbolic links to files and folders and reports them`() {
        val outside = root.resolve("outside").createDirectories()
        outside.resolve("secret.txt").writeBytes(ByteArray(1))
        val keys = root.resolve("repo/keys").createDirectories()
        keys.resolve("a.pem").writeBytes(ByteArray(1))
        keys.resolve("link.txt").createSymbolicLinkPointingTo(outside.resolve("secret.txt"))
        keys.resolve("linked-dir").createSymbolicLinkPointingTo(outside)

        val listing = fileManager.listTree(keys.toString())

        assertEquals(listOf("a.pem"), listing.files)
        assertEquals(listOf("link.txt", "linked-dir"), listing.skippedLinks.sorted())
    }

    @Test
    fun `listing an empty or missing folder yields nothing`() {
        val empty = root.resolve("repo/empty").createDirectories()

        assertEquals(DirectoryListing(), fileManager.listTree(empty.toString()))
        assertEquals(DirectoryListing(), fileManager.listTree(root.resolve("missing").toString()))
    }

    @Test
    fun `tells a symbolic link apart and deletes a single file`() {
        val target = root.resolve("repo/keys").createDirectories()
        val link = root.resolve("worktree").createDirectories().resolve("keys")
        link.createSymbolicLinkPointingTo(target)
        val file = root.resolve("worktree/notes.txt").also { it.writeBytes(ByteArray(1)) }

        assertTrue(fileManager.isSymbolicLink(link.toString()))
        assertFalse(fileManager.isSymbolicLink(file.toString()))
        fileManager.deleteFile(link.toString())
        fileManager.deleteFile(file.toString())
        fileManager.deleteFile(root.resolve("worktree/missing").toString())

        assertFalse(link.exists(LinkOption.NOFOLLOW_LINKS))
        assertFalse(file.exists())
        assertTrue(target.exists())
    }

    @Test
    fun `a file system without symbolic links or without the privilege reports them unsupported`() {
        val unsupported = NioFileManager { _, _ -> throw UnsupportedOperationException("no links") }
        val unprivileged = NioFileManager { link, _ -> throw FileSystemException(link.toString(), null, "A required privilege is not held by the client") }
        val other = NioFileManager { link, _ -> throw FileSystemException(link.toString(), null, "disk full") }
        val link = root.resolve("link").toString()

        assertFailsWith<SymbolicLinksNotSupported> { unsupported.createSymbolicLink("target", link) }
        assertFailsWith<SymbolicLinksNotSupported> { unprivileged.createSymbolicLink("target", link) }
        assertFailsWith<FileSystemException> { other.createSymbolicLink("target", link) }
    }

    @Test
    fun `lists the names in a folder sorted and nothing for a missing folder`() {
        root.resolve("b.txt").writeBytes(ByteArray(0))
        root.resolve("a").createDirectories()

        assertEquals(listOf("a", "b.txt"), fileManager.listNames(root.toString()))
        assertNull(fileManager.listNames(root.resolve("missing").toString()))
    }

    @Test
    fun `unreadable entries are skipped by every walk and unreadable properties read as empty`() {
        assumeTrue(root.fileSystem.supportedFileAttributeViews().contains("posix"))
        val locked = root.resolve("locked").createDirectories()
        locked.resolve("inside.txt").writeBytes(ByteArray(3))
        root.resolve("open.txt").writeBytes(ByteArray(2))
        val properties = root.resolve("app.properties")
        properties.writeBytes("a=1".toByteArray())
        Files.setPosixFilePermissions(locked, emptySet())
        Files.setPosixFilePermissions(properties, emptySet())
        try {
            assertEquals(listOf("open.txt"), fileManager.listTree(root.toString()).files.filter { it.endsWith(".txt") })
            assertEquals(5L, fileManager.directorySize(root.toString()))
            assertTrue(fileManager.modificationStamps(root.toString()).none { it.first.endsWith("inside.txt") })
            assertEquals(emptyMap(), fileManager.readProperties(properties.toString()))
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"))
            Files.setPosixFilePermissions(properties, PosixFilePermissions.fromString("rw-------"))
        }
    }

    @Test
    fun `a readable file or folder has no unavailable reason`() {
        val file = root.resolve("local.properties").also { Files.writeString(it, "sdk.dir=/opt/android") }

        assertNull(fileManager.unavailableReason(file.toString()))
        assertNull(fileManager.unavailableReason(root.toString()))
    }

    @Test
    fun `a missing path is unavailable as missing`() {
        assertEquals(UnavailableReason.Missing, fileManager.unavailableReason(root.resolve("absent").toString()))
    }

    @Test
    fun `a link to nothing is unavailable as a broken link naming its target`() {
        val target = root.resolve("missing/keys.properties")
        val link = root.resolve("keys.properties").createSymbolicLinkPointingTo(target)

        assertEquals(UnavailableReason.BrokenLink(target.toString()), fileManager.unavailableReason(link.toString()))
    }

    @Test
    fun `a file without read permission is unavailable as unreadable`() {
        val file = root.resolve("secret.properties").also { Files.writeString(it, "token=placeholder") }
        if (!file.toFile().setReadable(false, false) || Files.isReadable(file)) return

        try {
            assertIs<UnavailableReason.Unreadable>(fileManager.unavailableReason(file.toString()))
        } finally {
            file.toFile().setReadable(true, false)
        }
    }

    @Test
    fun `a read-only folder keeps its files from being deleted`() {
        assumeTrue(root.fileSystem.supportedFileAttributeViews().contains("posix"))
        val folder = root.resolve("sealed").createDirectories()
        folder.resolve("file.txt").writeBytes(ByteArray(1))
        Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("r-x------"))
        try {
            assertFailsWith<AccessDeniedException> { fileManager.deleteRecursively(folder.toString()) }
        } finally {
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwx------"))
        }
    }

    @Test
    fun `a folder entry that can't be read is listed as unreadable`() {
        val keys = root.resolve("keys").createDirectories()
        Files.writeString(keys.resolve("a.pem"), "a")
        val private = keys.resolve("private").createDirectories()
        Files.writeString(private.resolve("b.pem"), "b")
        if (!private.toFile().setReadable(false, false) || Files.isReadable(private)) return

        try {
            assertEquals(DirectoryListing(files = listOf("a.pem"), unreadable = listOf("private")), fileManager.listTree(keys.toString()))
            assertIs<UnavailableReason.Unreadable>(fileManager.unavailableReason(private.toString()))
        } finally {
            private.toFile().setReadable(true, false)
        }
    }

    @Test
    fun `a file that can't be opened as a file is unavailable naming why`() {
        assumeTrue(root.fileSystem.supportedFileAttributeViews().contains("posix"))
        val socket = root.resolve("agent.sock")
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { server ->
            server.bind(UnixDomainSocketAddress.of(socket))

            assertIs<UnavailableReason.Unreadable>(fileManager.unavailableReason(socket.toString()))
        }
    }

    @Test
    fun `a read failure names its reason or its kind`() {
        assertEquals("permission denied", readFailureReason(AccessDeniedException("/a")))
        assertEquals("locked", readFailureReason(AccessDeniedException("/a", null, "locked")))
        assertEquals("FileSystemException", readFailureReason(FileSystemException("/a")))
        assertEquals("loop", readFailureReason(FileSystemException("/a", null, "loop")))
        assertEquals("broken", readFailureReason(java.io.IOException("broken")))
        assertEquals("IOException", readFailureReason(java.io.IOException()))
    }

    @Test
    fun `deleting a tree stops at a directory that couldn't be read`() {
        val deleted = mutableListOf<Path>()
        val deleter = TreeDeleter { deleted.add(it) }

        assertFailsWith<java.io.IOException> { deleter.postVisitDirectory(root, java.io.IOException("unreadable")) }
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun `links that Windows refuses for lack of privilege aren't supported and other failures pass through`() {
        val privileged = NioFileManager(linkTo = { _, _ -> throw FileSystemException("/l", null, "A required privilege is not held by the client") })
        val other = NioFileManager(linkTo = { _, _ -> throw FileSystemException("/l", null, "disk full") })
        val reasonless = NioFileManager(linkTo = { _, _ -> throw FileSystemException("/l") })
        val denied = NioFileManager(linkTo = { _, _ -> throw AccessDeniedException("/l", null, "privilege") })

        assertFailsWith<SymbolicLinksNotSupported> { privileged.createSymbolicLink("/t", root.resolve("a").toString()) }
        assertFailsWith<FileSystemException> { other.createSymbolicLink("/t", root.resolve("b").toString()) }
        assertFailsWith<FileSystemException> { reasonless.createSymbolicLink("/t", root.resolve("c").toString()) }
        assertFailsWith<AccessDeniedException> { denied.createSymbolicLink("/t", root.resolve("d").toString()) }
    }

    @Test
    fun `missing paths have no time, names, sameness or containment, and special files aren't listed`() {
        val folder = Files.createDirectories(root.resolve("special"))
        ProcessBuilder("mkfifo", folder.resolve("pipe").toString()).start().waitFor()

        assertEquals(emptyList(), fileManager.listTree(folder.toString()).files)
        assertNull(fileManager.lastModifiedMillis(root.resolve("missing").toString()))
        assertNull(fileManager.listNames(root.resolve("missing").toString()))
        assertFalse(fileManager.isInside(root.resolve("missing").toString(), root.toString()))
        val dangling = Files.createSymbolicLink(root.resolve("dangling"), root.resolve("nowhere"))
        assertFalse(fileManager.isSymbolicLinkTo(dangling.toString(), root.resolve("nowhere").toString()))
    }

    @Test
    fun `a file that can't be read has no text`() {
        val file = Files.writeString(root.resolve("secret.txt"), "x")
        file.toFile().setReadable(false)
        try {
            assertNull(fileManager.readText(file.toString()))
        } finally {
            file.toFile().setReadable(true)
        }
    }
}
