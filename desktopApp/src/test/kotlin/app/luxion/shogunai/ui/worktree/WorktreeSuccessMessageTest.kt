package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.OrcaOutcome
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WorktreeSuccessMessageTest {

    private val strings = EnglishStrings
    private val path = "/home/dev/projects/TASK-123"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `each success has its own message`() {
        assertEquals(strings.worktreeCreated("TASK-123"), strings.successMessage(WorktreeSuccess.Created(path)))
        assertEquals(strings.worktreeCreatedCommandsSkipped("TASK-123"), strings.successMessage(WorktreeSuccess.Created(path, commandsSkipped = true)))
        assertEquals(strings.worktreesRepairedAfterMove, strings.successMessage(WorktreeSuccess.RepairedAfterMove))
        assertEquals(strings.branchDeleted("feature/TASK-123"), strings.successMessage(WorktreeSuccess.BranchDeleted("feature/TASK-123")))
        assertEquals(strings.openedInHerdr("TASK-123"), strings.successMessage(WorktreeSuccess.OpenedInHerdr(path, HerdrOutcome.OPENED)))
        assertEquals(strings.agentStartedInHerdr("TASK-123"), strings.successMessage(WorktreeSuccess.OpenedInHerdr(path, HerdrOutcome.STARTED_AGENT)))
        assertEquals(strings.focusedInHerdr("TASK-123"), strings.successMessage(WorktreeSuccess.OpenedInHerdr(path, HerdrOutcome.FOCUSED)))
        assertEquals(strings.agentStartedInOrca("TASK-123"), strings.successMessage(WorktreeSuccess.OpenedInOrca(path, OrcaOutcome.STARTED_AGENT)))
        assertEquals(strings.focusedInOrca("TASK-123"), strings.successMessage(WorktreeSuccess.OpenedInOrca(path, OrcaOutcome.FOCUSED)))
    }

    @Test
    fun `a cleanup message lists only what it did`() {
        assertEquals(strings.worktreesCleanedUp(2), strings.successMessage(WorktreeSuccess.CleanedUp(removedCount = 2)))
        assertEquals(
            listOf(
                strings.worktreesCleanedUp(2),
                strings.daemonsStopped(1, 1, failed = 0),
                strings.emulatorsStopped(1),
                strings.simulatorsDeleted(2),
                strings.derivedDataDeleted(3, formatSize(2048)),
                strings.composeProjectsTakenDown(1),
                strings.composeResourcesRemoved(4),
            ).joinToString(" · "),
            strings.successMessage(
                WorktreeSuccess.CleanedUp(
                    removedCount = 2,
                    stoppedDaemons = 1,
                    keptDaemons = 1,
                    stoppedEmulators = 1,
                    deletedSimulators = 2,
                    deletedDerivedDataFolders = 3,
                    derivedDataBytesFreed = 2048,
                    takenDownComposeProjects = 1,
                    removedComposeResources = 4,
                ),
            ),
        )
    }

    @Test
    fun `only a project's created worktree offers to copy its launch command`() {
        val repository = FakeWorktreeRepository()
        val board = uiBoard(listOf(repository))
        val created = WorktreeSuccess.Created(path)

        assertNotNull(PendingSuccess(repository.id, created).action(strings, board))
        assertNull(PendingSuccess(null, created).action(strings, board))
        assertNull(PendingSuccess("unknown", created).action(strings, board))
        assertNull(PendingSuccess(repository.id, WorktreeSuccess.RepairedAfterMove).action(strings, board))
    }
}
