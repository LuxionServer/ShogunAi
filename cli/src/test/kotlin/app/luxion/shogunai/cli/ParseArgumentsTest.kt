package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.usecase.EnvironmentFormat
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
    fun `new with an explicit start point`() {
        assertEquals(CliCommand.New(taskId = "TASK-123", from = "epic/redesign"), command("new", "TASK-123", "--from", "epic/redesign"))
    }

    @Test
    fun `new rejects missing or conflicting targets`() {
        assertTrue(usageError("new").contains("task id"))
        assertTrue(usageError("new", "TASK-1", "--branch", "main").contains("not both"))
        assertTrue(usageError("new", "TASK-1", "TASK-2").contains("single task id"))
        assertTrue(usageError("new", "--branch", "main", "--fix").contains("--fix"))
        assertTrue(usageError("new", "--branch", "main", "--from", "develop").contains("--from"))
    }

    @Test
    fun `options that need a value reject a missing one`() {
        assertEquals("--branch needs a value", usageError("new", "--branch"))
        assertEquals("--from needs a value", usageError("new", "TASK-1", "--from"))
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
    fun `status takes only the json flag`() {
        assertEquals(CliCommand.Status(), command("status"))
        assertEquals(CliCommand.Status(json = true), command("status", "--json"))
        assertEquals("unknown option '--yaml' for 'status'", usageError("status", "--yaml"))
        assertEquals("'status' takes no arguments, got 'extra'", usageError("status", "extra"))
    }

    @Test
    fun `clean flags`() {
        assertEquals(CliCommand.Clean(), command("clean"))
        assertEquals(CliCommand.Clean(confirmed = true, deleteBranches = true), command("clean", "--delete-branches", "-y"))
        assertEquals("unknown argument '--all' for 'clean'", usageError("clean", "--all"))
        assertEquals(CliCommand.Clean(confirmed = true, stopGradleDaemons = true), command("clean", "--yes", "--stop-gradle-daemons"))
        assertEquals(CliCommand.Clean(confirmed = true, deleteDerivedData = true), command("clean", "--yes", "--delete-derived-data"))
    }

    @Test
    fun `xcode commands`() {
        assertEquals(CliCommand.Xcode, command("xcode"))
        assertEquals(CliCommand.XcodeClean, command("xcode", "clean"))
        assertEquals("unknown subcommand 'build' for 'xcode', expected clean", usageError("xcode", "build"))
        assertEquals("'xcode clean' takes no arguments, got 'extra'", usageError("xcode", "clean", "extra"))
    }

    @Test
    fun `emulator command`() {
        assertEquals(CliCommand.Emulator(EmulatorAction.START), command("emulator", "start"))
        assertEquals(
            CliCommand.Emulator(EmulatorAction.START, "../TASK-123", "Pixel_8_API_35", noWindow = true),
            command("emulator", "start", "../TASK-123", "--avd", "Pixel_8_API_35", "--no-window"),
        )
        assertEquals(CliCommand.Emulator(EmulatorAction.STATUS, "../TASK-123"), command("emulator", "status", "../TASK-123"))
        assertEquals(CliCommand.Emulator(EmulatorAction.STOP), command("emulator", "STOP"))
        assertEquals("'emulator' needs an action: start, stop or status", usageError("emulator"))
        assertEquals("unknown action 'restart' for 'emulator', expected start, stop or status", usageError("emulator", "restart"))
        assertEquals("--avd and --no-window only apply to 'emulator start'", usageError("emulator", "stop", "--no-window"))
        assertEquals("--avd needs a value", usageError("emulator", "start", "--avd"))
        assertEquals(CliCommand.Clean(confirmed = true, stopEmulators = true), command("clean", "--yes", "--stop-emulators"))
    }

    @Test
    fun `simulator commands`() {
        assertEquals(CliCommand.Simulator(SimulatorAction.STATUS), command("simulator", "status"))
        assertEquals(
            CliCommand.Simulator(SimulatorAction.START, "../TASK-1", "iPhone 16", "iOS 18.5"),
            command("simulator", "start", "../TASK-1", "--device", "iPhone 16", "--runtime", "iOS 18.5"),
        )
        assertEquals("--device and --runtime only apply to 'simulator start'", usageError("simulator", "stop", "--device", "iPhone 16"))
        assertEquals("'simulator' needs an action: start, stop, status or gradle-init", usageError("simulator"))
        assertEquals(CliCommand.SimulatorGradleInit(install = true, confirmed = true), command("simulator", "gradle-init", "install", "--yes"))
        assertEquals(CliCommand.SimulatorGradleInit(install = false), command("simulator", "gradle-init", "uninstall"))
        assertEquals("'simulator gradle-init' needs install or uninstall", usageError("simulator", "gradle-init"))
        assertEquals(CliCommand.Clean(confirmed = true, deleteSimulators = true), command("clean", "--yes", "--delete-simulators"))
    }

    @Test
    fun `env command`() {
        assertEquals(CliCommand.Env(), command("env"))
        assertEquals(CliCommand.Env("../TASK-123", EnvironmentFormat.JSON), command("env", "../TASK-123", "--format", "json"))
        assertEquals(CliCommand.Env(null, EnvironmentFormat.DOTENV), command("env", "--format", "DOTENV"))
        assertEquals("unknown format 'yaml' for 'env', expected sh, dotenv or json", usageError("env", "--format", "yaml"))
        assertEquals("--format needs a value", usageError("env", "--format"))
        assertEquals("'env' takes at most one path", usageError("env", "a", "b"))
        assertEquals("unknown option '--all' for 'env'", usageError("env", "--all"))
    }

    @Test
    fun `gradle commands`() {
        assertEquals(CliCommand.Gradle, command("gradle"))
        assertEquals(CliCommand.GradleStop(), command("gradle", "stop"))
        assertEquals(CliCommand.GradleStop(listOf(4242, 4243)), command("gradle", "stop", "4242", "4243"))
        assertEquals("'gradle stop' takes process ids, got 'all'", usageError("gradle", "stop", "all"))
        assertEquals("'gradle stop' takes process ids, got '-1'", usageError("gradle", "stop", "-1"))
        assertEquals("unknown subcommand 'kill' for 'gradle', expected stop", usageError("gradle", "kill"))
    }

    @Test
    fun `hook events`() {
        assertEquals(CliCommand.HookWorktreeCreate, command("hook", "worktree-create"))
        assertEquals(CliCommand.HookWorktreeRemove, command("hook", "worktree-remove"))
        assertTrue(usageError("hook").contains("needs an event"))
        assertEquals(CliCommand.HookSessionStart, command("hook", "session-start"))
        assertEquals(CliCommand.HookHerdr, command("hook", "herdr"))
        assertTrue(usageError("hook", "stop").contains("unknown hook 'stop'"))
        assertTrue(usageError("hook", "worktree-create", "extra").contains("takes no arguments"))
    }

    @Test
    fun `unknown command`() {
        assertEquals("unknown command 'frobnicate'", usageError("frobnicate"))
    }
}
