package app.luxion.shogunai.domain.model

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorktreeErrorTest {

    @Test
    fun `fixed errors explain what to do`() {
        assertTrue(WorktreeError.RepositoryHasNoCommits.message.orEmpty().contains("make a first commit"))
        assertTrue(WorktreeError.AppNotAvailable.message.orEmpty().contains("install the app"))
        assertTrue(WorktreeError.HeadlessAppHasNoWindow.message.orEmpty().contains("no window"))
    }

    @Test
    fun `errors about a path name it`() {
        assertEquals("claude, codex is running in /projects/worktrees/task-123", WorktreeError.AgentRunningInWorktree("/projects/worktrees/task-123", listOf("claude", "codex")).message)
        assertEquals("No multirepo found at /projects/product", WorktreeError.MultirepoNotFound("/projects/product").message)
    }

    @Test
    fun `desktop taken summary leaves out the wait hint`() {
        val error = WorktreeError.DesktopTurnTaken(holder = null, waiting = 2)

        assertEquals("2 worktrees are waiting for the desktop first", error.summary)
        assertEquals(error.summary + ". Wait for it with --wait", error.message)
    }

    @Test
    fun `a full pool counts the worktrees waiting for one`() {
        val holder = PoolHolder(ResourceKind.EMULATOR, "/projects/worktrees/task-123", null, PoolHolderState.RUNNING, idle = false, lastUsed = Instant.EPOCH)

        assertTrue(WorktreeError.ResourcePoolFull(ResourceKind.EMULATOR, 1, listOf(holder), waiting = 3).message.orEmpty().contains("; 3 worktrees are waiting for one"))
    }

    @Test
    fun `wait text shows minutes and seconds`() {
        assertEquals("2 min", Duration.ofMinutes(2).toWaitText())
        assertEquals("2 min 5 s", Duration.ofSeconds(125).toWaitText())
        assertEquals("45 s", Duration.ofSeconds(45).toWaitText())
    }
    @Test
    fun `errors add their detail only when there is one`() {
        val command = listOf("git", "status")
        val withAndWithout = listOf(
            WorktreeError.GitCommandFailed(command, 1, "boom") to WorktreeError.GitCommandFailed(command, 1, " "),
            WorktreeError.LocalFileUnreadable("/a", "denied") to WorktreeError.LocalFileUnreadable("/a", null),
            WorktreeError.LocalFileUnreadable("/a", "denied") to WorktreeError.LocalFileUnreadable("/a", ""),
            WorktreeError.UpdateCheckFailed("offline") to WorktreeError.UpdateCheckFailed(null),
            WorktreeError.UpdateCheckFailed("offline") to WorktreeError.UpdateCheckFailed(" "),
            WorktreeError.InvalidRepositoryConfig("/a", "bad") to WorktreeError.InvalidRepositoryConfig("/a", null),
            WorktreeError.InvalidRepositoryConfig("/a", "bad") to WorktreeError.InvalidRepositoryConfig("/a", ""),
            WorktreeError.PostCreateCommandFailed("/w", command, 2, "boom") to WorktreeError.PostCreateCommandFailed("/w", command, null, ""),
            WorktreeError.DockerUnavailable("stopped") to WorktreeError.DockerUnavailable(null),
            WorktreeError.DockerUnavailable("stopped") to WorktreeError.DockerUnavailable(""),
            WorktreeError.EmulatorExitedEarly("emulator-5554", "log") to WorktreeError.EmulatorExitedEarly("emulator-5554", ""),
            WorktreeError.AndroidToolFailed(command, 1, "out") to WorktreeError.AndroidToolFailed(command, 1, ""),
            WorktreeError.AppleToolFailed(command, 1, "out") to WorktreeError.AppleToolFailed(command, 1, ""),
            WorktreeError.HeadlessAppLaunchFailed("/log", "log") to WorktreeError.HeadlessAppLaunchFailed("/log", ""),
            WorktreeError.HeadlessAppExitedEarly("/log", "log") to WorktreeError.HeadlessAppExitedEarly("/log", ""),
            WorktreeError.MacWindowToolFailed("denied") to WorktreeError.MacWindowToolFailed(""),
            WorktreeError.HeadlessAppStopFailed("stuck") to WorktreeError.HeadlessAppStopFailed(null),
            WorktreeError.HeadlessAppStopFailed("stuck") to WorktreeError.HeadlessAppStopFailed(""),
            WorktreeError.BranchAlreadyCheckedOut("main", "/repo") to WorktreeError.BranchAlreadyCheckedOut("main", null),
            WorktreeError.TerminalLaunchFailed(TerminalId.TMUX, "gone") to WorktreeError.TerminalLaunchFailed(TerminalId.TMUX, ""),
            WorktreeError.EditorLaunchFailed(EditorId.XCODE, "gone") to WorktreeError.EditorLaunchFailed(EditorId.XCODE, ""),
            WorktreeError.ClaudeDesktopLaunchFailed("gone") to WorktreeError.ClaudeDesktopLaunchFailed(""),
            WorktreeError.FolderOpenFailed("/a", "gone") to WorktreeError.FolderOpenFailed("/a", ""),
            WorktreeError.WorktreeFolderInUse("/w", true, command, 1, "busy") to WorktreeError.WorktreeFolderInUse("/w", false, command, 1, ""),
            WorktreeError.TourCreationFailed(command, "boom") to WorktreeError.TourCreationFailed(command, ""),
        )

        withAndWithout.forEach { (detailed, plain) ->
            val plainMessage = plain.message.orEmpty()
            assertTrue(detailed.message.orEmpty().length > plainMessage.length, plainMessage)
            assertTrue(plainMessage.trimEnd() == plainMessage && !plainMessage.endsWith(":"), plainMessage)
        }
        assertEquals("Post-creation command failed (exit code 2): git status\nboom", withAndWithout[7].first.message)
        assertEquals("Branch 'main' is already checked out at /repo", withAndWithout[18].first.message)
    }

    @Test
    fun `a device wait names the device with its article`() {
        val emulator = WorktreeError.DeviceWaitTimedOut(ResourceKind.EMULATOR, Duration.ofSeconds(30), 1, emptyList())
        val simulator = WorktreeError.DeviceWaitTimedOut(ResourceKind.IOS_SIMULATOR, Duration.ofSeconds(30), 2, emptyList())

        assertTrue(emulator.message.orEmpty().startsWith("Waited 30 s for an emulator"))
        assertTrue(simulator.message.orEmpty().startsWith("Waited 30 s for a ios simulator"))
    }

    @Test
    fun `holders show why they can't be released`() {
        val base = PoolHolder(ResourceKind.EMULATOR, "/projects/worktrees/task-123", null, PoolHolderState.RUNNING, idle = false, lastUsed = Instant.EPOCH)
        val working = base.copy(agentState = AgentState.WORKING)
        val leased = base.copy(leaseExpiresAt = Instant.EPOCH)

        val message = WorktreeError.ResourcePoolFull(ResourceKind.EMULATOR, 3, listOf(base, working, leased)).message.orEmpty()

        assertTrue(message.contains("(running)"), message)
        assertTrue(message.contains("(running, agent working)"), message)
        assertTrue(message.contains("(running, lease until ${Instant.EPOCH.toLocalClockTime()})"), message)
    }

    @Test
    fun `a desktop wait names who still holds the desktop`() {
        val turn = DesktopTurn("/repo/.git/worktrees/task-123", "/projects/worktrees/task-123", 0, 0)

        assertTrue(WorktreeError.DesktopTurnWaitTimedOut(Duration.ofSeconds(5), turn).message.orEmpty().contains(", still taken by "))
        assertTrue(WorktreeError.DesktopTurnWaitTimedOut(Duration.ofSeconds(5), null).message.orEmpty().endsWith(", still behind other waiters"))
        assertTrue(WorktreeError.DesktopTurnTaken(turn, waiting = 1).summary.endsWith("; 1 worktree is waiting for it"))
        assertTrue(WorktreeError.DesktopTurnTaken(turn).summary.startsWith("The desktop is taken by "))
    }

    @Test
    fun `a locked worktree names its lock reason when there is one`() {
        val command = listOf("git", "worktree", "remove", "/w/TASK-1")

        assertEquals(
            "/w/TASK-1 is locked (on a USB drive); remove it forcing it to override the lock",
            WorktreeError.WorktreeLocked("/w/TASK-1", "on a USB drive", command, 128, "locked").message,
        )
        assertEquals(
            "/w/TASK-1 is locked; remove it forcing it to override the lock",
            WorktreeError.WorktreeLocked("/w/TASK-1", null, command, 128, "locked").message,
        )
    }
}
