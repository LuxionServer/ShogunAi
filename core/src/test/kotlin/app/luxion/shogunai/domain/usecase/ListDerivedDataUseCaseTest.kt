package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeDerivedDataSource.Companion.derivedData
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListDerivedDataUseCaseTest {

    private val main = Worktree(path = "/home/dev/projects/main-repo", branch = "develop", isMain = true)
    private val nested = Worktree(path = "/home/dev/projects/main-repo/.claude/worktrees/task", branch = "worktree-task")
    private val sibling = Worktree(path = "/home/dev/projects/TASK-123", branch = "feature/TASK-123")
    private val worktrees = listOf(main, nested, sibling)

    private val mainProject = "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj"
    private val nestedProject = "/home/dev/projects/main-repo/.claude/worktrees/task/iosApp/iosApp.xcodeproj"
    private val siblingProject = "/home/dev/projects/TASK-123/iosApp/iosApp.xcodeproj"
    private val otherProject = "/home/dev/projects/other/Other.xcodeproj"
    private val goneProject = "/home/dev/projects/gone/Gone.xcodeproj"
    private val volumeProject = "/Volumes/Work/app/App.xcodeproj"

    private fun useCase(source: FakeDerivedDataSource) = ListDerivedDataUseCase(source)

    @Test
    fun `attributes a folder to the worktree that contains its workspace path`() = runTest {
        val source = FakeDerivedDataSource(
            existing = listOf(derivedData("MainRepo", mainProject)),
            existingProjects = setOf(mainProject),
        )

        val listing = useCase(source)(worktrees, includeCompilationCache = false, measure = DerivedDataMeasure.ALL).getOrThrow()

        assertEquals(DerivedDataOwner.Worktree(main.path), listing.folders.single().owner)
        assertEquals(listOf(mainProject), listing.ofWorktrees().map { it.folder.workspacePath })
    }

    @Test
    fun `attributes a folder to the deepest worktree when worktrees are nested`() = runTest {
        val source = FakeDerivedDataSource(
            existing = listOf(derivedData("Nested", nestedProject)),
            existingProjects = setOf(nestedProject),
        )

        val listing = useCase(source)(worktrees, includeCompilationCache = false, measure = DerivedDataMeasure.ALL).getOrThrow()

        assertEquals(DerivedDataOwner.Worktree(nested.path), listing.folders.single().owner)
    }

    @Test
    fun `attributes a folder to another project when the path exists outside the worktrees`() = runTest {
        val source = FakeDerivedDataSource(
            existing = listOf(derivedData("Other-hash", otherProject)),
            existingProjects = setOf(otherProject),
        )

        val listing = useCase(source)(worktrees, includeCompilationCache = false, measure = DerivedDataMeasure.ALL).getOrThrow()

        assertEquals(DerivedDataOwner.OtherProject, listing.folders.single().owner)
    }

    @Test
    fun `marks an orphan when the project is gone`() = runTest {
        val source = FakeDerivedDataSource(existing = listOf(derivedData("Gone-hash", goneProject)))

        val listing = useCase(source)(worktrees, includeCompilationCache = false, measure = DerivedDataMeasure.ALL).getOrThrow()

        assertEquals(DerivedDataOwner.Orphaned, listing.folders.single().owner)
        assertEquals(listOf(goneProject), listing.orphans.map { it.workspacePath })
    }

    @Test
    fun `reports a missing project on an unavailable volume as not orphaned`() = runTest {
        val source = FakeDerivedDataSource(
            existing = listOf(derivedData("Vol-hash", volumeProject), derivedData("Gone-hash", goneProject)),
            unavailableVolumes = setOf("/Volumes/Work"),
        )

        val listing = useCase(source)(worktrees, includeCompilationCache = false, measure = DerivedDataMeasure.ALL).getOrThrow()

        assertEquals(DerivedDataOwner.UnavailableVolume, listing.folders[0].owner)
        assertEquals(DerivedDataOwner.Orphaned, listing.folders[1].owner)
        assertEquals(listOf(goneProject), listing.orphans.map { it.workspacePath })
    }

    @Test
    fun `includes the compilation cache only when requested`() = runTest {
        val source = FakeDerivedDataSource(existing = emptyList(), cacheBytes = 42_000_000L)

        val without = useCase(source)(worktrees, includeCompilationCache = false, measure = DerivedDataMeasure.ALL).getOrThrow()
        val with = useCase(source)(worktrees, includeCompilationCache = true, measure = DerivedDataMeasure.ALL).getOrThrow()

        assertNull(without.compilationCacheBytes)
        assertEquals(42_000_000L, with.compilationCacheBytes)
    }

    @Test
    fun `measures nothing when asked for no measuring`() = runTest {
        val source = FakeDerivedDataSource(
            existing = listOf(derivedData("MainRepo", mainProject, sizeBytes = null), derivedData("Gone", goneProject, sizeBytes = null)),
            existingProjects = setOf(mainProject),
        )

        val listing = useCase(source)(worktrees, includeCompilationCache = false, measure = DerivedDataMeasure.NONE).getOrThrow()

        assertTrue(source.measured.isEmpty())
        assertTrue(listing.folders.all { it.folder.sizeBytes == null })
    }

    @Test
    fun `measures only the orphans when asked for orphans`() = runTest {
        val source = FakeDerivedDataSource(
            existing = listOf(derivedData("MainRepo", mainProject, sizeBytes = null), derivedData("Gone", goneProject, sizeBytes = null)),
            existingProjects = setOf(mainProject),
        )
        source.sizes[derivedData("Gone", goneProject).path] = 42L

        val listing = useCase(source)(worktrees, includeCompilationCache = false, measure = DerivedDataMeasure.ORPHANS).getOrThrow()

        assertEquals(listOf(derivedData("Gone", goneProject).path), source.measured)
        assertEquals(listOf(null, 42L), listing.folders.map { it.folder.sizeBytes })
    }

    @Test
    fun `measures every folder when asked for all`() = runTest {
        val source = FakeDerivedDataSource(
            existing = listOf(derivedData("MainRepo", mainProject, sizeBytes = null), derivedData("Gone", goneProject, sizeBytes = null)),
            existingProjects = setOf(mainProject),
        )

        val listing = useCase(source)(worktrees, includeCompilationCache = false, measure = DerivedDataMeasure.ALL).getOrThrow()

        assertEquals(2, source.measured.size)
        assertTrue(listing.folders.all { it.folder.sizeBytes != null })
    }
}
