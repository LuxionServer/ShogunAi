package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.AgentActivity
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DeviceWaitProgress
import app.luxion.shogunai.domain.model.FlutterPubGetFailure
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.usecase.BranchDeletion
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CliTextTest {

    private fun holder(name: String, agentState: AgentState?) =
        PoolHolder(ResourceKind.IOS_SIMULATOR, "/projects/worktrees/$name", "/projects/main-repo/.git/worktrees/$name", PoolHolderState.BOOTED, idle = false, lastUsed = Instant.EPOCH, agentState = agentState, releaseRank = 1)

    @Test
    fun `wait progress names the queue position and why each holder is kept`() {
        val progress = DeviceWaitProgress(ResourceKind.IOS_SIMULATOR, 2, 3, 2, listOf(holder("TASK-1", AgentState.WORKING), holder("TASK-2", null)), null)

        assertEquals(
            "waiting for a simulator: 2nd of 3 in the queue, limit 2: main-repo · TASK-1 booted, agent working; main-repo · TASK-2 booted",
            waitProgressLine(progress),
        )
    }

    @Test
    fun `ordinals follow English suffixes`() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23).map(::ordinal))
    }

    @Test
    fun `a branch Git couldn't delete is explained with or without Git's reason`() {
        assertEquals("error: branch is checked out", BranchDeletion.KeptFailed("error: branch is checked out").keptReason())
        assertEquals("Git couldn't delete it", BranchDeletion.KeptFailed(" ").keptReason())
        assertEquals("Git couldn't delete it: error: branch is checked out", BranchDeletion.KeptFailed("error: branch is checked out").keptBecause())
        assertEquals("Git couldn't delete it: no reason given", BranchDeletion.KeptFailed("").keptBecause())
    }

    @Test
    fun `an unexpected pub get failure keeps its message`() {
        assertEquals("pubspec.yaml is unreadable", flutterPubGetReason(FlutterPubGetFailure.Unexpected("pubspec.yaml is unreadable")))
    }

    @Test
    fun `an agent's activity without a tool it waits on has a null waitingOn`() {
        val json = agentJson(RunningAgent("claude", activity = AgentActivity(prompt = "fix it")))

        assertEquals(JsonNull, json.getValue("activity").jsonObject.getValue("waitingOn"))
    }
}
