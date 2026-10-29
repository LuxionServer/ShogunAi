# Proposal

## Why

The desktop app, opened from the Dock, gets macOS's minimal `PATH` (`/usr/bin:/bin:/usr/sbin:/sbin`). `fix/login-shell-path` taught the `shogun` status check to read the login shell's `PATH`, but every other part of the code still reads the environment on its own: the processes the app starts (Git, a Herdr server, Orca, post-creation commands) only get the Homebrew folders added to that minimal `PATH`, Herdr and Orca are looked up in fixed folders, and about thirty classes read `System.getenv` or `user.home` wherever they need them. A `shogun` installed in `~/.local/bin` is therefore missing in a Herdr pane the app opened, and each new call site is a new chance to get it wrong. It is the first item of the roadmap's "Reliability on any machine", and `shogun doctor` builds on it.

## What Changes

- A `HostEnvironment` port in `core` with one implementation: the home folder, the user's shell, the `PATH` folders (and where they came from) and any other variable, resolved once when the process starts.
- The desktop app resolves the `PATH` from the login shell, as `fix/login-shell-path` already did for the status check; the CLI, which is started from a terminal, a hook or a runner that already passes the user's environment, takes its own `PATH`. Both add the usual install folders when nothing better is known.
- Every child process gets the host environment's `PATH` instead of `HomebrewPath.merge`, which is removed.
- Herdr, Orca and every other tool are found through `ExecutableLocator` over the host environment, instead of each client's own fixed folder list.
- No production code outside the implementation reads `System.getenv` or `user.home`: the CLI's `main` and the desktop app's `AppContainer` build the host environment and pass its values down. A test enforces it.
- `ShellLoginShellPath` and the `LoginShellPath` port are replaced by the new component.
- A regression test, written first, reproduces the Dock's minimal `PATH` and checks that a child process (a Herdr server stand-in) finds a `shogun` that only the login shell's `PATH` has.
- Out of scope: `shogun doctor` and "Check installation", which come next and consume the API described in `design.md`.

## Capabilities

### New Capabilities

- `host-environment`: how ShogunAi learns the machine's environment (home folder, shell, `PATH`) once per process, the fallbacks when it can't, and that every command it runs and every tool it looks for use it.

### Modified Capabilities

None. `command-line-tool-setup`'s "Judge the PATH by the login shell" keeps its behaviour; it now reads the `PATH` from the host environment.

## Impact

- `core`: new `domain/io/HostEnvironment`, `infrastructure/SystemHostEnvironment`; `ProcessBuilderShellCommandExecutor`, `ExecutableLocator`, `CliHerdrClient`, `CliOrcaClient` and every class with a `user.home` or `System.getenv` default take their values from the caller; `ShellLoginShellPath`, `LoginShellPath` and `HomebrewPath` are removed.
- `cli`: `Main.kt` builds the host environment and passes it to `ShogunCli` and the infrastructure.
- `desktopApp`: `AppContainer` builds it once (resolution on a background thread, as today) and passes it everywhere.
- Tests: the affected classes' tests pass values explicitly; a new architecture test forbids environment reads outside the implementation.
- No new dependencies; no change to the files ShogunAi writes.
