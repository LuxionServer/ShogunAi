package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildTurnTest {

    private fun slot(id: String) = BuildSlot(
        "slot-$id",
        "/projects/main-repo/.git/worktrees/$id",
        "/worktrees/$id",
        "./gradlew test",
        0L,
        ProcessIdentity(1, 1),
    )

    @Test
    fun `a slot names its project and worktree`() {
        assertEquals("main-repo · TASK-1", slot("TASK-1").displayName)
        assertEquals("/projects/main-repo", slot("TASK-1").repositoryPath)
    }

    @Test
    fun `taken slots name their builds and the waiters`() {
        assertEquals(
            "The only build slot is taken by main-repo · TASK-1",
            BuildTurnError.SlotsTaken(1, listOf(slot("TASK-1")), 0).message,
        )
        assertEquals(
            "All 2 build slots are taken by main-repo · TASK-1, main-repo · TASK-2; 1 build is waiting",
            BuildTurnError.SlotsTaken(2, listOf(slot("TASK-1"), slot("TASK-2")), 1).message,
        )
        assertEquals(
            "Other builds are waiting for a slot first; 3 builds are waiting",
            BuildTurnError.SlotsTaken(2, emptyList(), 3).message,
        )
    }

    @Test
    fun `the empty state has no limit and nothing running or waiting`() {
        assertEquals(BuildTurnState(null, emptyList(), emptyList()), BuildTurnState.EMPTY)
    }
}
