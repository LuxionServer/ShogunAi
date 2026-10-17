package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeDerivedDataSource.Companion.derivedData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeleteOrphanedDerivedDataUseCaseTest {

    private val mainProject = "/home/dev/projects/main-repo/iosApp/iosApp.xcodeproj"
    private val otherProject = "/home/dev/projects/other/Other.xcodeproj"
    private val orphanProject = "/home/dev/projects/gone/Gone.xcodeproj"
    private val restoredProject = "/home/dev/projects/gone/Gone2.xcodeproj"
    private val volumeProject = "/Volumes/Work/app/App.xcodeproj"

    private fun useCase(source: FakeDerivedDataSource) = DeleteOrphanedDerivedDataUseCase(source)

    @Test
    fun `deletes an orphan and reports the space freed`() = runTest {
        val orphan = derivedData("Gone-hash", orphanProject, sizeBytes = 3_000_000_000L)
        val source = FakeDerivedDataSource(
            existing = listOf(orphan, derivedData("MainRepo-hash", mainProject)),
            existingProjects = setOf(mainProject),
        )

        val deletion = useCase(source)().getOrThrow()

        assertEquals(listOf(orphan.path), deletion.deleted.map { it.path })
        assertEquals(3_000_000_000L, deletion.bytesFreed)
        assertTrue(deletion.failures.isEmpty())
        assertEquals(listOf(orphan.path), source.deleted)
    }

    @Test
    fun `keeps the folders of live projects and unavailable volumes`() = runTest {
        val source = FakeDerivedDataSource(
            existing = listOf(
                derivedData("MainRepo-hash", mainProject),
                derivedData("Other-hash", otherProject),
                derivedData("Vol-hash", volumeProject),
            ),
            existingProjects = setOf(mainProject, otherProject),
            unavailableVolumes = setOf("/Volumes/Work"),
        )

        val deletion = useCase(source)().getOrThrow()

        assertTrue(deletion.deleted.isEmpty())
        assertTrue(deletion.failures.isEmpty())
        assertTrue(source.deleted.isEmpty())
    }

    @Test
    fun `checks the project again before deleting and skips one restored in the meantime`() = runTest {
        val first = derivedData("Gone-hash", orphanProject, sizeBytes = 1_000_000_000L)
        val second = derivedData("Gone2-hash", restoredProject, sizeBytes = 2_000_000_000L)
        val source = FakeDerivedDataSource(existing = listOf(first, second))
        source.beforeDelete = { source.existingProjects += restoredProject }

        val deletion = useCase(source)().getOrThrow()

        assertEquals(listOf(first.path), deletion.deleted.map { it.path })
        assertEquals(1_000_000_000L, deletion.bytesFreed)
        assertTrue(deletion.failures.isEmpty())
        assertEquals(listOf(first.path), source.deleted)
    }

    @Test
    fun `reports a failed deletion without stopping the rest`() = runTest {
        val stuck = derivedData("Stuck-hash", orphanProject)
        val fine = derivedData("Fine-hash", restoredProject)
        val source = FakeDerivedDataSource(
            existing = listOf(stuck, fine),
            failDeletionFor = setOf(stuck.path),
        )

        val deletion = useCase(source)().getOrThrow()

        assertEquals(listOf(fine.path), deletion.deleted.map { it.path })
        assertEquals(listOf(stuck.path), deletion.failures.map { it.path })
        assertEquals("can't delete ${stuck.path}", deletion.failures.single().detail)
    }
}
