package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeDerivedDataSource.Companion.derivedData
import app.luxion.shogunai.domain.model.BYTES_PER_GIGABYTE
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrimDerivedDataUseCaseTest {

    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val gb = BYTES_PER_GIGABYTE
    private val task1 = Worktree(path = "/home/dev/projects/TASK-1", branch = "feature/TASK-1")
    private val task2 = Worktree(path = "/home/dev/projects/TASK-2", branch = "feature/TASK-2")
    private val task3 = Worktree(path = "/home/dev/projects/TASK-3", branch = "feature/TASK-3")
    private val worktrees = listOf(task1, task2, task3)
    private val project1 = "/home/dev/projects/TASK-1/App.xcodeproj"
    private val project2 = "/home/dev/projects/TASK-2/App.xcodeproj"
    private val project3 = "/home/dev/projects/TASK-3/App.xcodeproj"
    private val otherProject = "/home/dev/projects/other/Other.xcodeproj"
    private val goneProject = "/home/dev/projects/gone/Gone.xcodeproj"
    private val volumeProject = "/Volumes/Work/app/App.xcodeproj"

    private fun folder(name: String, project: String, sizeGb: Long, accessed: Instant?): DerivedDataFolder =
        derivedData(name, project, sizeGb * gb).copy(lastAccessed = accessed)

    private fun daysAgo(days: Long) = now.minus(Duration.ofDays(days))

    private fun source(vararg folders: DerivedDataFolder, existing: Set<String> = setOf(project1, project2, project3, otherProject)) =
        FakeDerivedDataSource(
            existing = folders.toList(),
            existingProjects = existing,
            unavailableVolumes = setOf("/Volumes/Work"),
        )

    private fun useCase(source: FakeDerivedDataSource) = TrimDerivedDataUseCase(source, ListDerivedDataUseCase(source)) { now }

    private suspend fun trim(
        source: FakeDerivedDataSource,
        quotaGb: Long?,
        active: Set<String> = emptySet(),
        dryRun: Boolean = false,
    ) = useCase(source)(quotaGb?.let { it * gb }, worktrees, active, dryRun).getOrThrow()

    private fun names(source: FakeDerivedDataSource) = source.deleted.map { it.substringAfterLast('/') }

    @Test
    fun `usage counts every folder of the location whoever owns it`() = runTest {
        val source = source(
            folder("A", project1, 20, daysAgo(3)),
            folder("B", otherProject, 15, daysAgo(3)),
        )

        val result = trim(source, quotaGb = 100)

        assertEquals(35 * gb, result.usageBefore)
        assertTrue(source.deleted.isEmpty())
    }

    @Test
    fun `the compilation cache does not count`() = runTest {
        val source = source(folder("A", project1, 30, daysAgo(3))).also { it.cacheBytes = 10 * gb }

        assertEquals(30 * gb, trim(source, quotaGb = 100).usageBefore)
    }

    @Test
    fun `no quota releases nothing`() = runTest {
        val source = source(folder("A", project1, 500, daysAgo(30)))

        val result = trim(source, quotaGb = null)

        assertNull(result.quotaBytes)
        assertFalse(result.isExceeded)
        assertTrue(source.deleted.isEmpty())
    }

    @Test
    fun `orphans go before worktree folders`() = runTest {
        val source = source(
            folder("Old", project1, 20, daysAgo(30)),
            folder("Orphan", goneProject, 10, daysAgo(1)),
        )

        val result = trim(source, quotaGb = 25)

        assertEquals(listOf("Orphan"), names(source))
        assertEquals(20 * gb, result.usageAfter)
        assertFalse(result.isExceeded)
    }

    @Test
    fun `worktree folders are released oldest access first and only as many as needed`() = runTest {
        val source = source(
            folder("Tuesday", project2, 20, daysAgo(5)),
            folder("Monday", project1, 20, daysAgo(6)),
        )

        val result = trim(source, quotaGb = 25)

        assertEquals(listOf("Monday"), names(source))
        assertEquals(20 * gb, result.bytesFreed)
        assertEquals(20 * gb, result.usageAfter)
    }

    @Test
    fun `a folder without last access is the oldest`() = runTest {
        val source = source(
            folder("Dated", project1, 20, daysAgo(60)),
            folder("Undated", project2, 20, null),
        )

        trim(source, quotaGb = 25)

        assertEquals(listOf("Undated"), names(source))
    }

    @Test
    fun `folders of other projects and unavailable volumes are never released`() = runTest {
        val source = source(
            folder("Other", otherProject, 40, daysAgo(90)),
            folder("Volume", volumeProject, 40, daysAgo(90)),
            existing = setOf(otherProject),
        )

        val result = trim(source, quotaGb = 10)

        assertTrue(source.deleted.isEmpty())
        assertTrue(result.isExceeded)
        assertEquals(80 * gb, result.usageAfter)
    }

    @Test
    fun `a worktree whose agent is active is skipped`() = runTest {
        val source = source(
            folder("Busy", project1, 20, daysAgo(30)),
            folder("Idle", project2, 20, daysAgo(10)),
        )

        val result = trim(source, quotaGb = 25, active = setOf(task1.path))

        assertEquals(listOf("Idle"), names(source))
        assertEquals(listOf(DerivedDataOwner.Worktree(task1.path)), result.inUse.map { it.owner })
    }

    @Test
    fun `a folder accessed within the hour is in use`() = runTest {
        val source = source(folder("Recent", project1, 20, now.minus(Duration.ofMinutes(10))))

        val result = trim(source, quotaGb = 5)

        assertTrue(source.deleted.isEmpty())
        assertTrue(result.isExceeded)
        assertEquals(1, result.inUse.size)
    }

    @Test
    fun `a folder accessed more than an hour ago is released`() = runTest {
        val source = source(folder("Stale", project1, 20, now.minus(Duration.ofMinutes(90))))

        trim(source, quotaGb = 5)

        assertEquals(listOf("Stale"), names(source))
    }

    @Test
    fun `only folders in use leave the quota exceeded without deleting`() = runTest {
        val source = source(
            folder("A", project1, 20, daysAgo(2)),
            folder("B", project2, 20, daysAgo(2)),
        )

        val result = trim(source, quotaGb = 10, active = setOf(task1.path, task2.path))

        assertTrue(source.deleted.isEmpty())
        assertTrue(result.isExceeded)
        assertEquals(2, result.inUse.size)
    }

    @Test
    fun `a folder accessed between planning and deleting is skipped`() = runTest {
        val source = source(
            folder("First", project1, 20, daysAgo(10)),
            folder("Second", project2, 20, daysAgo(5)),
        )
        val original = source.existing
        source.onFolders = { calls ->
            if (calls == 2) source.existing = original.map { if (it.path.endsWith("First")) it.copy(lastAccessed = now) else it }
        }

        trim(source, quotaGb = 25)

        assertEquals(listOf("Second"), names(source))
    }

    @Test
    fun `a failed deletion is reported and the next candidate is tried`() = runTest {
        val source = source(
            folder("Broken", project1, 20, daysAgo(10)),
            folder("Fine", project2, 20, daysAgo(5)),
        )
        source.failDeletionFor = setOf(source.existing.first().path)

        val result = trim(source, quotaGb = 25)

        assertEquals(listOf("Fine"), names(source))
        assertEquals(1, result.failures.size)
    }

    @Test
    fun `a dry run reports what would be released and deletes nothing`() = runTest {
        val source = source(folder("Old", project1, 20, daysAgo(10)), folder("New", project2, 20, daysAgo(2)))

        val result = trim(source, quotaGb = 25, dryRun = true)

        assertTrue(source.deleted.isEmpty())
        assertEquals(listOf("Old"), result.released.map { it.folder.path.substringAfterLast('/') })
        assertEquals(20 * gb, result.usageAfter)
    }

    @Test
    fun `a trim is real unless asked for a dry run`() = runTest {
        val source = source(folder("A", project1, 1, daysAgo(30)))

        val result = useCase(source)(quotaBytes = null, worktrees, emptySet()).getOrThrow()

        assertFalse(result.dryRun)
    }
}
