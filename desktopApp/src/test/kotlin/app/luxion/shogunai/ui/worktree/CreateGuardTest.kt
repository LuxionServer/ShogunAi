package app.luxion.shogunai.ui.worktree

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreateGuardTest {

    @Test
    fun `existing branch mode is disabled when the selected branch is unavailable`() {
        assertFalse(isCreateEnabled(CreateMode.EXISTING_BRANCH, isTaskIdValid = true, isSelectedBranchAvailable = false))
    }

    @Test
    fun `existing branch mode is enabled when the selected branch is available`() {
        assertTrue(isCreateEnabled(CreateMode.EXISTING_BRANCH, isTaskIdValid = false, isSelectedBranchAvailable = true))
    }

    @Test
    fun `new branch mode depends only on the task id`() {
        assertTrue(isCreateEnabled(CreateMode.NEW_BRANCH, isTaskIdValid = true, isSelectedBranchAvailable = false))
        assertFalse(isCreateEnabled(CreateMode.NEW_BRANCH, isTaskIdValid = false, isSelectedBranchAvailable = true))
    }
}
