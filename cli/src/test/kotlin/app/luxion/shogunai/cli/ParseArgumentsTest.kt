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
    fun `help after a command asks for that command's help`() {
        assertEquals(CliCommand.CommandHelp("new"), command("new", "--help"))
        assertEquals(CliCommand.CommandHelp("clean"), command("clean", "--yes", "-h"))
        assertEquals(CliCommand.CommandHelp("hook"), command("hook", "herdr", "--help"))
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
    fun `new accepts fetch with a task id or a branch`() {
        assertEquals(CliCommand.New(taskId = "TASK-1", fetch = true), command("new", "TASK-1", "--fetch"))
        assertEquals(CliCommand.New(branch = "feature/x", fetch = true), command("new", "--fetch", "--branch", "feature/x"))
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
    fun `provision check with and without a path`() {
        assertEquals(CliCommand.Provision(check = true), command("provision", "--check"))
        assertEquals(CliCommand.Provision("../TASK-123", check = true), command("provision", "--check", "../TASK-123"))
        assertEquals(CliCommand.Provision("../TASK-123", check = true), command("provision", "../TASK-123", "--check"))
        assertTrue(usageError("provision", "--check", "a", "b").contains("at most one path"))
    }

    @Test
    fun `base takes an optional target, set and the json flag`() {
        assertEquals(ParsedArguments.Command(CliCommand.Base()), parseArguments(listOf("base")))
        assertEquals(
            ParsedArguments.Command(CliCommand.Base("TASK-1", "origin/develop", json = true)),
            parseArguments(listOf("base", "TASK-1", "--set", "origin/develop", "--json")),
        )
        assertEquals(ParsedArguments.UsageError("--set needs a value"), parseArguments(listOf("base", "--set")))
        assertEquals(ParsedArguments.UsageError("unknown option '--yaml' for 'base'"), parseArguments(listOf("base", "--yaml")))
        assertEquals(ParsedArguments.UsageError("'base' takes a single target, got 'a' and 'b'"), parseArguments(listOf("base", "a", "b")))
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
        assertEquals(CliCommand.Clean(confirmed = true, stopMavenDaemons = true), command("clean", "--yes", "--stop-maven-daemons"))
        assertEquals(CliCommand.Clean(confirmed = true, deleteDerivedData = true), command("clean", "--yes", "--delete-derived-data"))
    }

    @Test
    fun `xcode commands`() {
        assertEquals(CliCommand.Xcode(), command("xcode"))
        assertEquals(CliCommand.XcodeClean(), command("xcode", "clean"))
        assertEquals("unknown subcommand 'build' for 'xcode', expected clean or trim", usageError("xcode", "build"))
        assertEquals(CliCommand.XcodeTrim(), command("xcode", "trim"))
        assertEquals(CliCommand.XcodeTrim(dryRun = true), command("xcode", "trim", "--dry-run"))
        assertEquals("unknown argument 'extra' for 'xcode trim', expected --dry-run", usageError("xcode", "trim", "extra"))
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
        assertEquals(CliCommand.Gradle(), command("gradle"))
        assertEquals(CliCommand.GradleStop(), command("gradle", "stop"))
        assertEquals(CliCommand.GradleStop(listOf(4242, 4243)), command("gradle", "stop", "4242", "4243"))
        assertEquals("'gradle stop' takes process ids, got 'all'", usageError("gradle", "stop", "all"))
        assertEquals("'gradle stop' takes process ids, got '-1'", usageError("gradle", "stop", "-1"))
        assertEquals("unknown subcommand 'kill' for 'gradle', expected stop", usageError("gradle", "kill"))
    }

    @Test
    fun `maven commands`() {
        assertEquals(CliCommand.Maven(), command("maven"))
        assertEquals(CliCommand.MavenStop(), command("maven", "stop"))
        assertEquals(CliCommand.MavenStop(listOf(4242, 4243)), command("maven", "stop", "4242", "4243"))
        assertEquals(CliCommand.MavenStop(listOf(5L), json = true), command("maven", "stop", "5", "--json"))
        assertEquals("'maven stop' takes process ids, got 'all'", usageError("maven", "stop", "all"))
        assertEquals("unknown subcommand 'kill' for 'maven', expected stop", usageError("maven", "kill"))
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

    @Test
    fun `open takes a target, herdr, an agent and json`() {
        assertEquals(CliCommand.Open("TASK-123", herdr = true), command("open", "TASK-123", "--herdr"))
        assertEquals(CliCommand.Open("TASK-123", herdr = true, agent = "codex"), command("open", "--herdr", "--agent", "codex", "TASK-123"))
        assertEquals(CliCommand.Open("TASK-123", herdr = true, json = true), command("open", "TASK-123", "--herdr", "--json"))
        assertEquals(CliCommand.CommandHelp("open"), command("open", "--help"))
    }

    @Test
    fun `open needs a destination and knows its options`() {
        assertEquals(CliCommand.Open(null, herdr = true), command("open", "--herdr"))
        assertEquals("'open' needs where to open the worktree: --herdr", usageError("open", "TASK-123"))
        assertEquals("unknown option '--terminal' for 'open'", usageError("open", "TASK-123", "--terminal"))
        assertEquals("--agent needs a value", usageError("open", "TASK-123", "--herdr", "--agent"))
        assertEquals("'open' takes a single target, got 'TASK-1' and 'TASK-2'", usageError("open", "TASK-1", "TASK-2", "--herdr"))
    }

    @Test
    fun `new takes the repositories of a group`() {
        assertEquals(CliCommand.New("TASK-1", repos = listOf("app", "api")), command("new", "TASK-1", "--repos", "app,api"))
        assertEquals("--repos needs at least one repository alias", usageError("new", "TASK-1", "--repos", ","))
        assertEquals("--from doesn't apply to a group created with --repos", usageError("new", "TASK-1", "--repos", "app", "--from", "develop"))
        assertEquals("--worktrees-root doesn't apply to a group created with --repos", usageError("new", "TASK-1", "--repos", "app", "--worktrees-root", "/tmp/x"))
    }

    @Test
    fun `multirepo parses its actions`() {
        assertEquals(CliCommand.Multirepo(MultirepoAction.Init("../w")), command("multirepo", "init", "--worktrees-root", "../w"))
        assertEquals(CliCommand.Multirepo(MultirepoAction.Show, json = true), command("multirepo", "show", "--json"))
        assertEquals(CliCommand.Multirepo(MultirepoAction.AddRepo("app", "product-app", true)), command("multirepo", "add-repo", "app", "product-app", "--use-worktrees-root"))
        assertEquals(CliCommand.Multirepo(MultirepoAction.AddRepo("app", "product-app", false)), command("multirepo", "add-repo", "app", "product-app", "--keep-worktrees-folder"))
        assertEquals(CliCommand.Multirepo(MultirepoAction.Defaults(listOf("app", "api"))), command("multirepo", "defaults", "app,api"))
        assertEquals(CliCommand.Multirepo(MultirepoAction.Defaults(emptyList())), command("multirepo", "defaults", "--clear"))
        assertEquals(CliCommand.Multirepo(MultirepoAction.Agent("codex --full-auto", "Codex", useHeadroom = false)), command("multirepo", "agent", "codex --full-auto", "--name", "Codex"))
        assertEquals(CliCommand.Multirepo(MultirepoAction.Agent("codex", useHeadroom = true)), command("multirepo", "agent", "codex", "--headroom"))
        assertEquals(CliCommand.Multirepo(MultirepoAction.Agent("codex", useHeadroom = false)), command("multirepo", "agent", "codex", "--no-headroom"))
        assertEquals("--headroom and --no-headroom can't be combined", usageError("multirepo", "agent", "codex", "--headroom", "--no-headroom"))
        assertEquals(CliCommand.Multirepo(MultirepoAction.Delete), command("multirepo", "delete"))
        assertEquals("usage: shogun multirepo remove-repo <alias>", usageError("multirepo", "remove-repo"))
        assertEquals("unknown multirepo action 'rename'", usageError("multirepo", "rename"))
        assertEquals(CliCommand.Multirepo(MultirepoAction.WorkspaceDetect), command("multirepo", "workspace", "detect"))
        assertEquals(CliCommand.Multirepo(MultirepoAction.EnvList, json = true), command("multirepo", "env", "--json"))
        assertEquals(
            CliCommand.Multirepo(MultirepoAction.EnvSet("app", "API_URL", "http://localhost:${'$'}{port:api.http}")),
            command("multirepo", "env", "set", "app", "API_URL", "http://localhost:${'$'}{port:api.http}"),
        )
        assertEquals(CliCommand.Multirepo(MultirepoAction.EnvUnset("app", "API_URL")), command("multirepo", "env", "unset", "app", "API_URL"))
        assertEquals("usage: shogun multirepo env set <alias> <name> <template>", usageError("multirepo", "env", "set", "app", "API_URL"))
        assertEquals("usage: shogun multirepo env unset <alias> <name>", usageError("multirepo", "env", "unset", "app"))
        assertEquals("usage: shogun multirepo env [set <alias> <name> <template> | unset <alias> <name>]", usageError("multirepo", "env", "list"))
        assertEquals("usage: shogun multirepo workspace detect", usageError("multirepo", "workspace", "detect", "extra"))
        assertEquals("--use-worktrees-root and --keep-worktrees-folder can't be combined", usageError("multirepo", "add-repo", "a", "b", "--use-worktrees-root", "--keep-worktrees-folder"))
    }

    @Test
    fun `remove takes a target with force and delete-branch`() {
        assertEquals(CliCommand.Remove("TASK-123"), command("remove", "TASK-123"))
        assertEquals(CliCommand.Remove("TASK-123", force = true, deleteBranch = true), command("remove", "--force", "TASK-123", "--delete-branch"))
        assertEquals(CliCommand.Remove("TASK-123", forceDeleteBranch = true), command("remove", "TASK-123", "--force-delete-branch"))
        assertEquals(CliCommand.Remove("TASK-123", force = true), command("remove", "-f", "TASK-123"))
    }

    @Test
    fun `remove needs exactly one target and knows its options`() {
        assertTrue(usageError("remove").contains("needs a task id, path or branch"))
        assertTrue(usageError("remove", "a", "b").contains("single target"))
        assertTrue(usageError("remove", "a", "--bogus").contains("unknown option '--bogus'"))
    }

    @Test
    fun `repair takes no arguments`() {
        assertEquals(CliCommand.Repair(), command("repair"))
        assertTrue(usageError("repair", "now").contains("takes no arguments"))
    }

    @Test
    fun `json is accepted anywhere after the command`() {
        assertEquals(CliCommand.New(taskId = "TASK-1", json = true), command("new", "--json", "TASK-1"))
        assertEquals(CliCommand.New(taskId = "TASK-1", json = true), command("new", "TASK-1", "--json"))
        assertEquals(CliCommand.Provision(check = true, json = true), command("provision", "--json", "--check"))
        assertEquals(CliCommand.Trust(yes = true, json = true), command("trust", "--yes", "--json"))
        assertEquals(CliCommand.Remove("TASK-1", json = true), command("remove", "TASK-1", "--json"))
        assertEquals(CliCommand.Repair(json = true), command("repair", "--json"))
        assertEquals(CliCommand.Clean(confirmed = true, json = true), command("clean", "--json", "--yes"))
        assertEquals(CliCommand.Gradle(json = true), command("gradle", "--json"))
        assertEquals(CliCommand.GradleStop(listOf(5L), json = true), command("gradle", "stop", "5", "--json"))
        assertEquals(CliCommand.Xcode(json = true), command("xcode", "--json"))
        assertEquals(CliCommand.XcodeClean(json = true), command("xcode", "clean", "--json"))
        assertEquals(CliCommand.Emulator(EmulatorAction.STATUS, json = true), command("emulator", "status", "--json"))
        assertEquals(CliCommand.Simulator(SimulatorAction.STOP, json = true), command("simulator", "stop", "--json"))
    }

    @Test
    fun `json is an unknown option on commands without JSON output`() {
        assertEquals(
            ParsedArguments.UsageError("unknown option '--json' for 'hook session-start'"),
            parseArguments(listOf("hook", "session-start", "--json")),
        )
        assertTrue(parseArguments(listOf("simulator", "gradle-init", "install", "--json")) is ParsedArguments.UsageError)
        assertTrue(parseArguments(listOf("version", "--json")) is ParsedArguments.UsageError)
    }

    @Test
    fun `pool takes status or trim and accepts json`() {
        assertEquals(ParsedArguments.Command(CliCommand.Pool(PoolAction.STATUS)), parseArguments(listOf("pool", "status")))
        assertEquals(ParsedArguments.Command(CliCommand.Pool(PoolAction.TRIM, json = true)), parseArguments(listOf("pool", "trim", "--json")))
        assertIs<ParsedArguments.UsageError>(parseArguments(listOf("pool")))
        assertIs<ParsedArguments.UsageError>(parseArguments(listOf("pool", "status", "extra")))
    }

    @Test
    fun `guide parses a topic and its options`() {
        assertEquals(CliCommand.Guide(), command("guide"))
        assertEquals(CliCommand.Guide(topic = "parallel", plain = true), command("guide", "parallel", "--plain"))
        assertEquals(CliCommand.Guide(migrate = true, file = "AGENTS.md"), command("guide", "--migrate", "--file", "AGENTS.md"))
    }

    @Test
    fun `guide rejects unknown topics and misplaced options`() {
        assertEquals("unknown topic 'parties' for 'guide', expected worktrees, parallel, resources, cleanup", usageError("guide", "parties"))
        assertEquals("--file only applies to 'guide --migrate'", usageError("guide", "--file", "x"))
        assertEquals("--migrate can't be combined with a topic or --plain", usageError("guide", "worktrees", "--migrate"))
    }

    @Test
    fun `guide install parses its target`() {
        assertEquals(CliCommand.GuideBlock(GuideAction.INSTALL, agent = "gemini", yes = true), command("guide", "install", "--agent", "gemini", "--yes"))
        assertEquals(CliCommand.GuideBlock(GuideAction.UNINSTALL, project = true), command("guide", "uninstall", "--project"))
        assertEquals(CliCommand.GuideBlock(GuideAction.STATUS, json = true), command("guide", "status", "--json"))
    }

    @Test
    fun `guide install needs exactly one target and a known agent`() {
        assertEquals("'guide install' needs where: --agent <codex|gemini>, --project or --file <path>", usageError("guide", "install"))
        assertEquals("--agent, --project and --file can't be combined", usageError("guide", "install", "--project", "--file", "x"))
        assertEquals("unknown agent 'zed' for 'guide', expected codex, gemini", usageError("guide", "install", "--agent", "zed"))
        assertEquals("Cursor has no user-level instructions file; use --project (its AGENTS.md) or --file", usageError("guide", "install", "--agent", "cursor"))
    }
}
