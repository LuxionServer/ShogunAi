package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.BranchType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParseArgumentsTest {

    private fun command(vararg args: String): CliCommand =
        assertIs<ParsedArguments.Command>(parseArguments(args.toList())).command

    private fun usageError(vararg args: String): String =
        assertIs<ParsedArguments.UsageError>(parseArguments(args.toList())).message

    @Test
    fun `no arguments and help aliases print the help`() {
        assertEquals(CliCommand.Help, command())
        listOf("help", "-h", "--help").forEach { assertEquals(CliCommand.Help, command(it)) }
    }

    @Test
    fun `version aliases`() {
        listOf("version", "-v", "--version").forEach { assertEquals(CliCommand.Version, command(it)) }
    }

    @Test
    fun `new with a task id defaults to a feature branch`() {
        assertEquals(CliCommand.New(taskId = "TASK-123"), command("new", "TASK-123"))
    }

    @Test
    fun `new with fix and a worktrees root in any order`() {
        assertEquals(
            CliCommand.New(taskId = "TASK-123", branchType = BranchType.FIX, worktreesRoot = "../worktrees"),
            command("new", "--worktrees-root", "../worktrees", "TASK-123", "--fix"),
        )
    }

    @Test
    fun `new from an existing branch`() {
        assertEquals(CliCommand.New(branch = "hotfix/login"), command("new", "--branch", "hotfix/login"))
    }

    @Test
    fun `new rejects missing or conflicting targets`() {
        assertTrue(usageError("new").contains("task id"))
        assertTrue(usageError("new", "TASK-1", "--branch", "main").contains("not both"))
        assertTrue(usageError("new", "TASK-1", "TASK-2").contains("single task id"))
        assertTrue(usageError("new", "--branch", "main", "--fix").contains("--fix"))
    }

    @Test
    fun `options that need a value reject a missing one`() {
        assertEquals("--branch needs a value", usageError("new", "--branch"))
        assertEquals("--worktrees-root needs a value", usageError("new", "TASK-1", "--worktrees-root", "--fix"))
    }

    @Test
    fun `new rejects unknown options`() {
        assertEquals("unknown option '--force' for 'new'", usageError("new", "TASK-1", "--force"))
    }

    @Test
    fun `provision with and without a path`() {
        assertEquals(CliCommand.Provision(), command("provision"))
        assertEquals(CliCommand.Provision("../TASK-123"), command("provision", "../TASK-123"))
        assertTrue(usageError("provision", "a", "b").contains("at most one path"))
        assertTrue(usageError("provision", "--all").contains("unknown option"))
    }

    @Test
    fun `status takes no arguments`() {
        assertEquals(CliCommand.Status, command("status"))
        assertEquals("'status' takes no arguments, got '--json'", usageError("status", "--json"))
    }

    @Test
    fun `clean flags`() {
        assertEquals(CliCommand.Clean(), command("clean"))
        assertEquals(CliCommand.Clean(confirmed = true, deleteBranches = true), command("clean", "--delete-branches", "-y"))
        assertEquals("unknown argument '--all' for 'clean'", usageError("clean", "--all"))
    }

    @Test
    fun `hook events`() {
        assertEquals(CliCommand.HookWorktreeCreate, command("hook", "worktree-create"))
        assertEquals(CliCommand.HookWorktreeRemove, command("hook", "worktree-remove"))
        assertTrue(usageError("hook").contains("needs an event"))
        assertTrue(usageError("hook", "session-start").contains("unknown hook 'session-start'"))
        assertTrue(usageError("hook", "worktree-create", "extra").contains("takes no arguments"))
    }

    @Test
    fun `unknown command`() {
        assertEquals("unknown command 'frobnicate'", usageError("frobnicate"))
    }
}
