package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeDerivedDataSizeStore
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeDerivedDataSource.Companion.derivedData
import app.luxion.shogunai.domain.FakeSwiftBuildSource
import app.luxion.shogunai.domain.FakeSwiftBuildSource.Companion.swiftBuild
import app.luxion.shogunai.domain.io.SwiftBuildSource
import app.luxion.shogunai.domain.model.BYTES_PER_GIGABYTE
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataKind
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.MeasuredSize
import app.luxion.shogunai.domain.model.RememberedSize
import app.luxion.shogunai.domain.model.SwiftBuildDeletion
import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwiftBuildFoldersTest {

    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val gb = BYTES_PER_GIGABYTE
    private val task1 = Worktree(path = "/home/dev/projects/TASK-1", branch = "feature/TASK-1")
    private val task2 = Worktree(path = "/home/dev/projects/TASK-2", branch = "feature/TASK-2")
    private val worktrees = listOf(task1, task2)
    private val project1 = "/home/dev/projects/TASK-1/App.xcodeproj"

    private fun daysAgo(days: Long) = now.minus(Duration.ofDays(days))

    @Test
    fun `the build folders of package worktrees are listed as belonging to their worktree`() = runTest {
        val builds = FakeSwiftBuildSource(swiftBuild(task1.path, daysAgo(1)))
        val source = FakeDerivedDataSource(existing = listOf(derivedData("App", project1)), existingProjects = setOf(project1))

        val listing = ListDerivedDataUseCase(source, swiftBuilds = builds)(worktrees, false, DerivedDataMeasure.NONE, packageWorktrees = worktrees).getOrThrow()

        val build = listing.folders.single { it.folder.kind == DerivedDataKind.SWIFT_BUILD }
        assertEquals("/home/dev/projects/TASK-1/.build", build.folder.path)
        assertEquals(DerivedDataOwner.Worktree(task1.path), build.owner)
        assertEquals(2, listing.ofWorktrees().size)
        assertTrue(listing.orphans.isEmpty())
    }

    @Test
    fun `without package worktrees no build folder is read`() = runTest {
        val builds = FakeSwiftBuildSource(swiftBuild(task1.path, daysAgo(1)))

        val listing = ListDerivedDataUseCase(FakeDerivedDataSource(), swiftBuilds = builds)(worktrees, false, DerivedDataMeasure.ALL).getOrThrow()

        assertTrue(listing.folders.isEmpty())
    }

    @Test
    fun `a build folder is measured with its packages and remembered`() = runTest {
        val build = swiftBuild(task1.path, daysAgo(1))
        val builds = FakeSwiftBuildSource(build).apply {
            sizes[build.path] = 4 * gb
            packageSizes[build.path] = gb
        }
        val store = FakeDerivedDataSizeStore()

        val sizes = MeasureDerivedDataUseCase(FakeDerivedDataSource(), store, swiftBuilds = builds)(listOf(build)).toList()

        assertEquals(listOf(MeasuredSize(build.path, 4 * gb, packagesBytes = gb)), sizes)
        assertEquals(mapOf(build.path to RememberedSize(4 * gb, daysAgo(1), gb)), store.sizes)
    }

    @Test
    fun `an unchanged build folder uses its remembered size`() = runTest {
        val build = swiftBuild(task1.path, daysAgo(1))
        val builds = FakeSwiftBuildSource(build)
        val store = FakeDerivedDataSizeStore(mapOf(build.path to RememberedSize(4 * gb, daysAgo(1), gb)))

        val sizes = MeasureDerivedDataUseCase(FakeDerivedDataSource(), store, swiftBuilds = builds)(listOf(build)).toList()

        assertEquals(listOf(MeasuredSize(build.path, 4 * gb, packagesBytes = gb)), sizes)
        assertTrue(builds.measured.isEmpty())
    }

    @Test
    fun `the quota counts build folders and releases the least recently accessed folder of either kind`() = runTest {
        val derived = derivedData("App", project1, 20 * gb).copy(lastAccessed = daysAgo(1))
        val source = FakeDerivedDataSource(existing = listOf(derived), existingProjects = setOf(project1))
        val build = swiftBuild(task2.path, daysAgo(2))
        val builds = FakeSwiftBuildSource(build).apply { sizes[build.path] = 8 * gb }
        val list = ListDerivedDataUseCase(source, swiftBuilds = builds)
        val listing = list(worktrees, false, DerivedDataMeasure.ALL, packageWorktrees = worktrees).getOrThrow()

        val trim = TrimDerivedDataUseCase(source, list, builds) { now }(25 * gb, worktrees, emptySet(), measured = listing).getOrThrow()

        assertEquals(28 * gb, trim.usageBefore)
        assertEquals(listOf(build.path), builds.deleted)
        assertTrue(source.deleted.isEmpty())
        assertEquals(20 * gb, trim.usageAfter)
    }

    @Test
    fun `a build folder modified since it was planned is skipped`() = runTest {
        val build = swiftBuild(task1.path, daysAgo(3), sizeBytes = 8 * gb)
        val builds = FakeSwiftBuildSource(build)
        val source = FakeDerivedDataSource()
        val list = ListDerivedDataUseCase(source, swiftBuilds = builds)
        val listing = list(worktrees, false, DerivedDataMeasure.NONE, packageWorktrees = worktrees).getOrThrow()
        builds.folders[task1.path] = build.copy(lastAccessed = now.minus(Duration.ofMinutes(2)))

        val trim = TrimDerivedDataUseCase(source, list, builds) { now }(gb, worktrees, emptySet(), measured = listing).getOrThrow()

        assertTrue(builds.deleted.isEmpty())
        assertTrue(trim.isExceeded)
    }

    @Test
    fun `a build folder gone before its release is skipped`() = runTest {
        val build = swiftBuild(task1.path, daysAgo(3), sizeBytes = 8 * gb)
        val builds = FakeSwiftBuildSource(build)
        val source = FakeDerivedDataSource()
        val list = ListDerivedDataUseCase(source, swiftBuilds = builds)
        val listing = list(worktrees, false, DerivedDataMeasure.NONE, packageWorktrees = worktrees).getOrThrow()
        builds.folders.clear()

        TrimDerivedDataUseCase(source, list, builds) { now }(gb, worktrees, emptySet(), measured = listing).getOrThrow()

        assertTrue(builds.deleted.isEmpty())
    }

    @Test
    fun `unused build folders are deleted and the ones in use kept`() = runTest {
        val unused = swiftBuild(task1.path, daysAgo(1))
        val busy = swiftBuild(task2.path, daysAgo(1))
        val builds = FakeSwiftBuildSource(unused, busy).apply { sizes[unused.path] = 3 * gb }

        val deletion = DeleteUnusedSwiftBuildsUseCase(builds) { now }(worktrees, setOf("${task2.path}/")).getOrThrow()

        assertEquals(listOf(unused.path), builds.deleted)
        assertEquals(3 * gb, deletion.bytesFreed)
        assertEquals(listOf(busy), deletion.inUse)
        assertTrue(deletion.failures.isEmpty())
    }

    @Test
    fun `a build folder changed within the hour is in use`() = runTest {
        val recent = swiftBuild(task1.path, now.minus(Duration.ofMinutes(10)))
        val builds = FakeSwiftBuildSource(recent)

        val deletion = DeleteUnusedSwiftBuildsUseCase(builds) { now }(worktrees, emptySet()).getOrThrow()

        assertTrue(builds.deleted.isEmpty())
        assertEquals(listOf(recent), deletion.inUse)
    }

    @Test
    fun `a build folder rebuilt while it was measured is kept as in use`() = runTest {
        val build = swiftBuild(task1.path, daysAgo(1))
        val builds = FakeSwiftBuildSource(build).apply { sizes[build.path] = 2 * gb }
        builds.afterSize = { builds.folders[task1.path] = build.copy(lastAccessed = now) }

        val deletion = DeleteUnusedSwiftBuildsUseCase(builds) { now }(worktrees, emptySet()).getOrThrow()

        assertTrue(builds.deleted.isEmpty())
        assertEquals(listOf(build.copy(sizeBytes = 2 * gb)), deletion.inUse)
    }

    @Test
    fun `a build folder removed while it was measured is neither deleted nor reported`() = runTest {
        val build = swiftBuild(task1.path, daysAgo(1))
        val builds = FakeSwiftBuildSource(build)
        builds.afterSize = { builds.folders.clear() }

        val deletion = DeleteUnusedSwiftBuildsUseCase(builds) { now }(worktrees, emptySet()).getOrThrow()

        assertEquals(SwiftBuildDeletionSummary(0, 0, 0), deletion.summary())
    }

    @Test
    fun `a failed deletion is reported and the next folder is still deleted`() = runTest {
        val first = swiftBuild(task1.path, daysAgo(1))
        val second = swiftBuild(task2.path, daysAgo(1))
        val builds = FakeSwiftBuildSource(first, second).apply { failDeletionFor = setOf(first.path) }

        val deletion = DeleteUnusedSwiftBuildsUseCase(builds) { now }(worktrees, emptySet()).getOrThrow()

        assertEquals(listOf(second.path), builds.deleted)
        assertEquals(listOf(first.path), deletion.failures.map { it.path })
        assertEquals("can't delete ${first.path}", deletion.failures.single().detail)
    }

    @Test
    fun `a failure without a message says the delete failed`() = runTest {
        val build = swiftBuild(task1.path, daysAgo(1))
        val builds = object : SwiftBuildSource by FakeSwiftBuildSource(build) {
            override suspend fun delete(folder: DerivedDataFolder) = throw IllegalStateException()
        }

        val deletion = DeleteUnusedSwiftBuildsUseCase(builds) { now }(worktrees, emptySet()).getOrThrow()

        assertEquals("delete failed", deletion.failures.single().detail)
    }

    @Test
    fun `a deleted folder of unknown size frees nothing`() {
        val deletion = SwiftBuildDeletion(deleted = listOf(swiftBuild(task1.path, null), swiftBuild(task2.path, null, sizeBytes = 2 * gb)))

        assertEquals(2 * gb, deletion.bytesFreed)
    }

    @Test
    fun `no build source finds, measures and deletes nothing`() = runTest {
        val none = SwiftBuildSource.NONE
        val build = swiftBuild(task1.path, null)

        assertNull(none.folderOf(task1.path))
        assertEquals(0L, none.size(build))
        assertEquals(0L, none.packagesSize(build))
        none.delete(build)
    }

    private data class SwiftBuildDeletionSummary(val deleted: Int, val inUse: Int, val failures: Int)

    private fun SwiftBuildDeletion.summary() = SwiftBuildDeletionSummary(deleted.size, inUse.size, failures.size)
}
