package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataKind
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSwiftBuildSourceTest {

    private val worktree = "/home/dev/projects/TASK-123"
    private val build = "$worktree/.build"
    private val manifest = "$worktree/Package.swift"

    private fun files(
        existing: List<String> = listOf(manifest),
        directories: Set<String> = setOf(build),
        sizes: Map<String, Long> = emptyMap(),
    ) = FakeFileManager(existing = existing, directories = directories, sizes = sizes)

    @Test
    fun `a package worktree's build folder is found with its latest modification`() {
        val files = files(existing = listOf(manifest, "$build/build.db", "$build/workspace-state.json"))
        files.modifiedAt[build] = 1_000L
        files.modifiedAt["$build/build.db"] = 5_000L
        files.modifiedAt["$build/workspace-state.json"] = 3_000L

        val folder = FileSwiftBuildSource(files).folderOf("$worktree/")

        assertEquals(DerivedDataFolder(build, worktree, null, Instant.ofEpochMilli(5_000L), kind = DerivedDataKind.SWIFT_BUILD), folder)
    }

    @Test
    fun `a build folder that can't be listed takes its own modification`() {
        val files = files()
        files.unreadable += build
        files.modifiedAt[build] = 2_000L

        assertEquals(Instant.ofEpochMilli(2_000L), FileSwiftBuildSource(files).folderOf(worktree)?.lastAccessed)
    }

    @Test
    fun `a build folder without any modification time has no last access`() {
        assertNull(FileSwiftBuildSource(files()).folderOf(worktree)?.lastAccessed)
    }

    @Test
    fun `no folder without a manifest, without a build folder or when it is a link`() {
        val linked = files().apply { symbolicLinks[build] = "/elsewhere" }

        assertNull(FileSwiftBuildSource(files(existing = emptyList())).folderOf(worktree))
        assertNull(FileSwiftBuildSource(files(directories = emptySet())).folderOf(worktree))
        assertNull(FileSwiftBuildSource(linked).folderOf(worktree))
    }

    @Test
    fun `measures the folder and its checkouts and repositories`() = runTest {
        val files = files(
            directories = setOf(build, "$build/checkouts", "$build/repositories"),
            sizes = mapOf(build to 4_000L, "$build/checkouts" to 600L, "$build/repositories" to 200L),
        )
        val source = FileSwiftBuildSource(files)
        val folder = source.folderOf(worktree)!!

        assertEquals(4_000L, source.size(folder))
        assertEquals(800L, source.packagesSize(folder))
    }

    @Test
    fun `a build folder without packages has none`() = runTest {
        val source = FileSwiftBuildSource(files())

        assertEquals(0L, source.packagesSize(source.folderOf(worktree)!!))
    }

    @Test
    fun `deletes a package's build folder`() = runTest {
        val files = files()
        val source = FileSwiftBuildSource(files)

        source.delete(source.folderOf(worktree)!!)

        assertEquals(listOf(build), files.deletedRecursively)
    }

    @Test
    fun `refuses to delete anything that isn't a package's build folder`() = runTest {
        val files = files(directories = setOf(build, "$worktree/Sources"))
        val source = FileSwiftBuildSource(files)
        val sources = DerivedDataFolder("$worktree/Sources", worktree, null, null, kind = DerivedDataKind.SWIFT_BUILD)
        val elsewhere = DerivedDataFolder("/home/dev/other/.build", "/home/dev/other", null, null, kind = DerivedDataKind.SWIFT_BUILD)

        assertFailsWith<IllegalStateException> { source.delete(sources) }
        assertFailsWith<IllegalStateException> { source.delete(elsewhere) }
        assertTrue(files.deletedRecursively.isEmpty())
    }
}
