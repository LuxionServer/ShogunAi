package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.AgentActivity
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DeviceWaitProgress
import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.DesktopWaitProgress
import app.luxion.shogunai.domain.model.FlutterPubGetFailure
import app.luxion.shogunai.domain.model.HeadlessAppPaths
import app.luxion.shogunai.domain.model.HeadlessAppStart
import app.luxion.shogunai.domain.model.HeadlessAppState
import app.luxion.shogunai.domain.model.HeadlessAppStatus
import app.luxion.shogunai.domain.model.MacPermission
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.UnresolvedReason
import app.luxion.shogunai.domain.usecase.BranchDeletion
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonPrimitive

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

    @Test
    fun `wait progress names a leased holder's lease`() {
        val leased = holder("TASK-3", null).copy(releaseRank = null, leaseExpiresAt = Instant.EPOCH)

        assertTrue(waitProgressLine(DeviceWaitProgress(ResourceKind.IOS_SIMULATOR, 1, 1, 1, listOf(leased), null)).contains("TASK-3 booted, lease until "))
    }

    @Test
    fun `desktop wait progress names the holder or says the desktop is free for an earlier waiter`() {
        val turn = DesktopTurn("/projects/main-repo/.git/worktrees/TASK-1", "/projects/worktrees/TASK-1", 0, 0)

        assertTrue(desktopWaitProgressLine(DesktopWaitProgress(2, 3, turn)).startsWith("waiting for the desktop: 2nd of 3 in line, held by main-repo · TASK-1 until "))
        assertEquals("waiting for the desktop: 1st of 2 in line, free for an earlier waiter", desktopWaitProgressLine(DesktopWaitProgress(1, 2, null)))
    }

    private fun headlessPaths(home: String?) = HeadlessAppPaths("/state", home, "/state/run.log", "/state/app.log", "/state/app.pid", "/state/captures")

    @Test
    fun `headless status shows its home only when it has one`() {
        val status = HeadlessAppStatus(HeadlessAppState.STOPPED, null, hasWindow = false, hidden = false, paths = headlessPaths(null), grantedPermissions = MacPermission.entries.toSet())

        assertEquals(listOf("stopped", "log: /state/run.log"), headlessStatusLines(status))
        assertEquals(listOf("stopped", "home: /home", "log: /state/run.log"), headlessStatusLines(status.copy(paths = headlessPaths("/home"))))
    }

    @Test
    fun `headless start json names the missing permission or leaves it null`() {
        val started = HeadlessAppStart(1, alreadyRunning = false, hidden = true, missingPermission = null, paths = headlessPaths(null))

        assertEquals(JsonNull, headlessStartJson("/w", started).getValue("missingPermission"))
        assertEquals("accessibility", headlessStartJson("/w", started.copy(missingPermission = MacPermission.ACCESSIBILITY)).getValue("missingPermission").jsonPrimitive.content)
    }

    @Test
    fun `migrate names the file to compare or the one the person points to`() {
        assertTrue(migrateText("CLAUDE.md").contains("Read CLAUDE.md and compare it with the rules above by id."))
        assertTrue(migrateText(null).contains("Read the AGENTS.md the person points you to and compare it with the rules above by id."))
    }

    @Test
    fun `a path is absolute when it starts at the root or the system says so`() {
        assertEquals("/projects/main-repo", absolutePath("/projects/main-repo", "/home/dev"))
        assertEquals("C:/projects/main-repo", absolutePath("C:/projects/main-repo", "/home/dev") { it.startsWith("C:") })
        assertEquals("/home/dev/projects/main-repo", absolutePath("projects/../projects/main-repo", "/home/dev"))
    }

    @Test
    fun `the agent's process is the closest ancestor named claude`() {
        val current = ProcessHandle.current()

        assertEquals(current.pid(), claudeAncestorPid(current) { if (it == current) "/usr/local/bin/claude" else "" })
        assertNull(claudeAncestorPid(current) { "" })
    }

    @Test
    fun `memory beyond terabytes stays in terabytes`() {
        assertEquals("1024.0 TB", ShogunCli.formatMemory(1024L * 1024 * 1024 * 1024 * 1024))
    }

    @Test
    fun `every reason a shared variable can't be resolved is explained`() {
        assertEquals("'api' hasn't reserved the port 'http' yet", unresolvedReason(UnresolvedReason.PortNotReserved("api", "http")))
        assertEquals("the worktree of 'api' can't be read: gone", unresolvedReason(UnresolvedReason.WorktreeUnreadable("api", "gone")))
    }
}
