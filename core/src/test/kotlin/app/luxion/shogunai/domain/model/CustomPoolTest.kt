package app.luxion.shogunai.domain.model

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomPoolTest {

    private val pool = CustomPool(
        name = "db",
        max = 3,
        acquire = listOf(parsePoolTemplate("./scripts/db-up.sh"), parsePoolTemplate("\${slot}"), parsePoolTemplate("\${worktree}")),
        release = listOf(parsePoolTemplate("./scripts/db-down.sh"), parsePoolTemplate("\${slot}")),
        variables = listOf(CustomPoolVariable("DATABASE_URL", parsePoolTemplate("postgres://localhost/app_\${slot}"))),
    )

    @Test
    fun `commands and variables are rendered with the slot and the worktree name`() {
        assertEquals(listOf("./scripts/db-up.sh", "2", "TASK-1"), pool.acquireCommand(2, "TASK-1"))
        assertEquals(listOf("./scripts/db-down.sh", "2"), pool.releaseCommand(2, "TASK-1"))
        assertEquals(listOf("DATABASE_URL" to "postgres://localhost/app_2"), pool.variablesFor(2, "TASK-1"))
        assertEquals(listOf("./scripts/db-up.sh", "\${slot}", "\${worktree}"), pool.acquireSource)
        assertEquals(listOf("./scripts/db-down.sh", "\${slot}"), pool.releaseSource)
    }

    @Test
    fun `a pool without release has no release command`() {
        val withoutRelease = pool.copy(release = null)

        assertNull(withoutRelease.releaseCommand(1, "TASK-1"))
        assertNull(withoutRelease.releaseSource)
    }

    @Test
    fun `shell characters stay literal in one argument`() {
        assertEquals("app_1; rm -rf ~", parsePoolTemplate("app_\${slot}; rm -rf ~").render(1, "TASK-1"))
    }

    @Test
    fun `a double dollar is a literal dollar`() {
        assertEquals("cost\$1", parsePoolTemplate("cost$$\${slot}").render(1, "TASK-1"))
    }

    @Test
    fun `unknown and unclosed placeholders are rejected`() {
        assertTrue(assertFailsWith<InvalidPoolTemplate> { parsePoolTemplate("http://localhost:\${port}") }.message.contains("\${port}"))
        assertTrue(assertFailsWith<InvalidPoolTemplate> { parsePoolTemplate("app_\${slot") }.message.contains("unclosed"))
    }

    @Test
    fun `pool names are lowercase letters, digits and hyphens up to 32 characters`() {
        assertTrue(isValidPoolName("db"))
        assertTrue(isValidPoolName("gpu-2"))
        assertTrue(isValidPoolName("a".repeat(32)))
        assertFalse(isValidPoolName("a".repeat(33)))
        assertFalse(isValidPoolName("My DB"))
        assertFalse(isValidPoolName("-db"))
        assertFalse(isValidPoolName(""))
    }

    @Test
    fun `a lease knows its holder, expiry and pool`() {
        val lease = CustomPoolLease("/projects/main-repo/.git", "/projects/main-repo", "db", 1, "/projects/main-repo/.git/worktrees/TASK-1/", "/worktrees/TASK-1", 0L, 60_000L)

        assertEquals("main-repo · TASK-1", lease.displayName)
        assertEquals(Instant.ofEpochMilli(60_000L + 1_200_000L), lease.expiresAt(Duration.ofMinutes(20)))
        assertTrue(lease.belongsTo("/projects/main-repo/.git", "db"))
        assertFalse(lease.belongsTo("/projects/main-repo/.git", "cache"))
        assertFalse(lease.belongsTo("/projects/api/.git", "db"))
        assertTrue(lease.heldBy("/projects/main-repo/.git/worktrees/TASK-1"))
        assertFalse(lease.heldBy("/projects/main-repo/.git/worktrees/TASK-2"))
    }

    @Test
    fun `the state finds a worktree's slot, place in line and loss`() {
        val admin = "/projects/main-repo/.git/worktrees/TASK-1"
        val lease = CustomPoolLease("/r/.git", "/r", "db", 2, admin, "/worktrees/TASK-1", 0L, 0L)
        val holder = CustomPoolHolder(lease, Instant.EPOCH, AgentState.WORKING, running = false)
        val ticket = WaitTicket("t", "/projects/main-repo/.git/worktrees/TASK-2", "/worktrees/TASK-2", ProcessIdentity(1, null), 0L)
        val loss = CustomPoolLoss("/r/.git", "db", 1, "/projects/main-repo/.git/worktrees/TASK-3", "/worktrees/TASK-3", 5L, "main-repo · TASK-1")
        val state = CustomPoolState(pool, listOf(holder), listOf(ticket), listOf(loss))

        assertEquals(holder, state.heldBy(admin))
        assertNull(state.heldBy("/projects/main-repo/.git/worktrees/TASK-2"))
        assertEquals(1 to ticket, state.waitingOf("/projects/main-repo/.git/worktrees/TASK-2"))
        assertNull(state.waitingOf(admin))
        assertEquals(loss, state.lossOf("/projects/main-repo/.git/worktrees/TASK-3"))
        assertNull(state.lossOf(admin))
        assertEquals(CustomPoolHolderNote("main-repo · TASK-1", 2, 0L, true), holder.note)
        assertEquals("main-repo · TASK-1", holder.displayName)
    }

    @Test
    fun `the ticket request names the repository and the pool`() {
        assertEquals("/projects/main-repo/.git\ndb", customPoolTicketRequest("/projects/main-repo/.git", "db"))
    }

    @Test
    fun `a full pool lists its holders, the waiters and how to free a slot`() {
        val leased = CustomPoolHolderNote("main-repo · TASK-1", 1, Instant.parse("2026-01-01T12:00:00Z").toEpochMilli(), false)
        val working = CustomPoolHolderNote("main-repo · TASK-2", 2, 0L, true)

        val none = CustomPoolProblem.Full("db", 2, listOf(leased, working), 0).message
        val one = CustomPoolProblem.Full("db", 2, emptyList(), 1).message
        val two = CustomPoolProblem.Full("db", 2, emptyList(), 2).message

        assertTrue(none.startsWith("Pool \"db\" is full (2 of 2 in use); main-repo · TASK-1 holds slot 1, leased until "))
        assertTrue(none.contains("main-repo · TASK-2 holds slot 2, its agent is working"))
        assertTrue(none.endsWith("--wait, or free one with `shogun pool release db` in the worktree that holds it"))
        assertTrue(one.contains("; 1 worktree is waiting."))
        assertTrue(two.contains("; 2 worktrees are waiting."))
    }

    @Test
    fun `the other problems explain themselves`() {
        val full = CustomPoolProblem.Full("db", 1, emptyList(), 0)

        assertEquals("The repository declares no pool \"cache\" in .shogunai.json; it declares db, gpu", CustomPoolProblem.UnknownPool("cache", listOf("db", "gpu")).message)
        assertEquals("The repository declares no pool \"cache\" in .shogunai.json; it declares no pools", CustomPoolProblem.UnknownPool("cache", emptyList()).message)
        assertTrue(CustomPoolProblem.NotApproved("db", "/projects/main-repo").message.contains("`shogun trust`"))
        assertEquals("Waited 5 min for a slot of pool \"db\": Pool \"db\" is full (0 of 1 in use)", CustomPoolProblem.WaitTimedOut(Duration.ofMinutes(5), full).message)
        assertEquals(
            "The acquire command of pool \"db\" failed (exit code 1) for TASK-1: ./up.sh 1\nno database",
            CustomPoolProblem.CommandFailed("db", CustomPoolCommandKind.ACQUIRE, "/worktrees/TASK-1/", listOf("./up.sh", "1"), 1, "no database").message,
        )
        assertEquals(
            "The release command of pool \"db\" failed for TASK-1: ./down.sh",
            CustomPoolProblem.CommandFailed("db", CustomPoolCommandKind.RELEASE, "C:\\worktrees\\TASK-1", listOf("./down.sh"), null, " ").message,
        )
        assertEquals(full.message, CustomPoolFailure(full).message)
    }
}
