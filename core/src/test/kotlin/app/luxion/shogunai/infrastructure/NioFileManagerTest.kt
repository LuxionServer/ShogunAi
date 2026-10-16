package app.luxion.shogunai.infrastructure

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NioFileManagerTest {

    private val root = createTempDirectory("nio-file-manager-test")
    private val fileManager = NioFileManager()

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
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
}
