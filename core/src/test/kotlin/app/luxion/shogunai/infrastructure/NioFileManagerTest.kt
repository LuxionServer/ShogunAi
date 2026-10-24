package app.luxion.shogunai.infrastructure

import java.nio.file.Files
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            fileManager.listFiles(keys.toString()).sorted(),
        )
    }

    @Test
    fun `listing skips symbolic links to files and folders`() {
        val outside = root.resolve("outside").createDirectories()
        outside.resolve("secret.txt").writeBytes(ByteArray(1))
        val keys = root.resolve("repo/keys").createDirectories()
        keys.resolve("a.pem").writeBytes(ByteArray(1))
        keys.resolve("link.txt").createSymbolicLinkPointingTo(outside.resolve("secret.txt"))
        keys.resolve("linked-dir").createSymbolicLinkPointingTo(outside)

        assertEquals(listOf("a.pem"), fileManager.listFiles(keys.toString()))
    }

    @Test
    fun `listing an empty or missing folder yields nothing`() {
        val empty = root.resolve("repo/empty").createDirectories()

        assertEquals(emptyList(), fileManager.listFiles(empty.toString()))
        assertEquals(emptyList(), fileManager.listFiles(root.resolve("missing").toString()))
    }
}
