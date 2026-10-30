# Design

## Context

See proposal.md for the motivation. Where the environment is read today:

- `ShellLoginShellPath` (behind the `LoginShellPath` port) reads the login shell's `PATH` once; only `AppContainer` uses it, for `CommandLineTool` and the two `ExecutableLocator`s it builds.
- `ProcessBuilderShellCommandExecutor` sets every child's `PATH` to `HomebrewPath.merge` of the JVM's `PATH`: `/opt/homebrew/bin`, `/opt/homebrew/sbin` and `/usr/local/bin` in front of it. From the Dock that is all a Herdr server, its panes, Git or a post-creation command get.
- `CliHerdrClient` and `CliOrcaClient` look for their binary in their own fixed folders (`~/.local/bin`, `~/.cargo/bin`, `/opt/homebrew/bin`, `/usr/local/bin`) and then in an `ExecutableLocator` over the JVM's `PATH`.
- `ExecutableLocator` defaults its `PATH`, `LOCALAPPDATA`, `ProgramFiles` and `PATHEXT` to `System.getenv`; `defaultGradleUserHome` reads `GRADLE_USER_HOME`; `AppContainer` reads `CLAUDE_CONFIG_DIR` twice.
- About thirty constructors default a path to `System.getProperty("user.home")`, three of them in `domain/usecase` (`IosSimulatorContribution`, `OrcaWorktreeReader`, `TourLocation`).

## Goals / Non-Goals

**Goals:**

- One component that knows the home folder, the shell, the `PATH` and the other variables, built once by each entry point and passed down.
- Child processes and tool lookups use its `PATH`.
- A test keeps new code from reading the environment directly.

**Non-Goals:**

- `shogun doctor` and "Check installation" (next change).
- Reading the login shell from the CLI, see below.
- Other JVM properties (`user.dir`, `user.name`, `java.home`, `jpackage.app-path`, `os.name` through `HostOs.current()`): they describe the running process, not the user's environment.
- Variables of a child process other than `PATH` (`HOME` and `SHELL` already reach the app from the Dock and are inherited unchanged).

## Decisions

### The port: `HostEnvironment`

`domain/io/HostEnvironment.kt`, the API `shogun doctor` will consume:

```kotlin
interface HostEnvironment {
    val os: HostOs
    val home: String
    val shell: String?
    val path: HostPath
    fun variable(name: String): String?
}

data class HostPath(val folders: List<String>, val source: HostPathSource)

enum class HostPathSource { LOGIN_SHELL, PROCESS, FALLBACK }
```

- `home`: `user.home` of the JVM (which the headless app overrides per worktree, so it stays the one source the JVM and the app agree on).
- `shell`: `SHELL`, blank treated as absent.
- `path`: resolved on first access, then cached. `LOGIN_SHELL` when the login shell's `PATH` was read (its folders, then the process's); `PROCESS` for the CLI and for Windows (the process's folders, then the usual install folders for the CLI); `FALLBACK` when the desktop app couldn't read the login shell (the process's folders, then the usual install folders). The doctor reports `source` so a `FALLBACK` reads as "couldn't read your login shell" instead of a silent guess.
- `variable(name)`: any other variable (`CLAUDE_CONFIG_DIR`, `GRADLE_USER_HOME`, `LOCALAPPDATA`, `ProgramFiles`, `PATHEXT`), from the process environment, case-insensitive on Windows as `System.getenv()` is.

`path` is a property that may block on its first access, for at most the login shell's time limit, as `LoginShellPath.folders()` did. Alternative considered: a `suspend` accessor. `ExecutableLocator.find` and the executor's process setup aren't `suspend`-friendly in all callers, and the wait happens once, usually before anyone asks because `AppContainer` starts it on a background thread.

### The implementation: `SystemHostEnvironment`

`infrastructure/SystemHostEnvironment` is the only production class that calls `System.getenv` or reads `user.home`. Its constructor takes the values (`os`, a variables map, `home`, an optional `ShellCommandExecutor` for the login shell, the time limit) so tests pass fakes; two factories build it from the real process:

- `SystemHostEnvironment.forCommandLine()`: no login shell.
- `SystemHostEnvironment.forDesktopApp()`: reads the login shell with its own `ProcessBuilderShellCommandExecutor` that leaves the child's `PATH` as inherited. It can't use the app's executor: that executor asks the host environment for its `PATH`, which is being resolved.

How the login shell is read (argv, `-l -i -c`, markers, 5 seconds, null input, any exit code) is unchanged from `ShellLoginShellPath`, whose code and tests move here. `ShellLoginShellPath` and `LoginShellPath` are deleted.

### The CLI doesn't start the login shell

`shogun` is started from a terminal, from Claude Code's hooks, from Herdr panes and from Orca's scripts, all of which pass the user's environment (Herdr's, after this change, because the app gives it the host `PATH`). Starting an interactive login shell on every hook call would add its start-up time to each one and run the user's rc files for every command. The CLI appends the usual install folders, which keeps what `HomebrewPath.merge` used to add, but after the user's own folders so their order wins.

### Child processes

`ProcessBuilderShellCommandExecutor` takes `path: (() -> List<String>)?`. When given, every child gets `PATH` set to those folders joined with the platform separator, before the command's explicit variables are applied (so an explicit `PATH` still wins). When `null`, the child inherits the JVM's `PATH` unchanged; only the login shell reader uses that. `HomebrewPath` is deleted: `/opt/homebrew/sbin`, the only folder it added that the usual install folders don't, is on the login shell's `PATH` whenever Homebrew's `shellenv` runs.

### Finding tools

`ExecutableLocator` is built from the host environment (`ExecutableLocator(environment)`), taking `os`, `home`, `path.folders` (read lazily), `LOCALAPPDATA`, `ProgramFiles` and `PATHEXT` from it. `CliHerdrClient` and `CliOrcaClient` take a `findExecutable: (String) -> String?` and drop their own folder lists and `userHome`; `AppContainer` and `ShogunCli` pass `locator::find`. Orca keeps its macOS application bundle as the last candidate.

### Passing the home folder down

Every constructor that defaulted to `user.home` or `System.getenv` loses the default and takes the value. To avoid repeating `~/.shogunai/<file>` in both entry points, `infrastructure/ShogunFiles(home)` names each file and folder under `~/.shogunai` once; `Main.kt` and `AppContainer` build it from `environment.home` and pass its paths. `ShogunCli` takes the `HostEnvironment` instead of `userHome`.

### Keeping it that way

A test in `core` scans the production sources of `core`, `cli` and `desktopApp` and fails on `System.getenv(` or `System.getProperty("user.home")` outside `SystemHostEnvironment.kt`. `FileKmpTestWiring` is the one exception: it writes a Gradle script that calls `System.getenv` inside a string.

### The regression test

`DockLaunchedChildProcessTest`, written before the executor change so it fails first: a temporary folder holds a `bin` with a fake `shogun` and a fake `herdr` (a `sh` script that prints `command -v shogun`, standing in for a Herdr server running a pane) and a fake login shell that prints that `bin` in its `PATH`. A `SystemHostEnvironment` built with the Dock's variables (`PATH=/usr/bin:/bin:/usr/sbin:/sbin`, `SHELL=<fake shell>`) feeds `ExecutableLocator`, the executor and `CliHerdrClient`; the test runs Herdr through them and expects the fake `shogun`'s path. With `HomebrewPath.merge` the child's `PATH` lacks that `bin`, so it fails. It runs on macOS and Linux only.

## Risks / Trade-offs

- [Every child of the desktop app waits for the login shell on its first command] → Resolution starts on a background thread when `AppContainer` is built and is bounded by the time limit; the first Git calls already wait for the app to load projects.
- [A user's login shell `PATH` puts a different Git or Java first than `HomebrewPath` did] → It's the same order their terminal uses, which is what they expect ShogunAi to run.
- [Removing constructor defaults touches many tests] → Mechanical; the compiler finds every call site.
- [The source scan is a text check] → It's a guard, not a proof; it catches the common mistake, and reviews catch the rest.
- [The CLI started from an environment with a minimal `PATH` that isn't one of the above] → It still gets the usual install folders, as before; `shogun doctor` will report it.

## Dependencies and follow-ups

- `shogun doctor` / "Check installation" (next change) consumes `HostEnvironment`: `path.source` and `path.folders` for "`shogun` on the login shell's `PATH`", `home` and `variable("CLAUDE_CONFIG_DIR")` for the hooks' settings, `ExecutableLocator(environment)` for Git, Claude Code, Herdr and Orca.
- `worktree-preflight`, `cli-e2e-tests`, `desktop-e2e-tests` and `packaged-smoke-test` run in parallel; the end-to-end tests that build `ShogunCli` or `AppContainer` will need the new `HostEnvironment` argument if they land after this change.
