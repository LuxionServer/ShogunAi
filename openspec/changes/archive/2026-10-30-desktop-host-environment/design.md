# Design

## Context

`HostEnvironment` (port) and `SystemHostEnvironment` already resolve the home, shell, `PATH` and variables once per process, and `HostEnvironmentSourceTest` keeps every other production file from reading `System.getenv` or `user.home`. Two pieces still sit outside it:

- `AppContainer` builds `SystemHostEnvironment.forDesktopApp()` in a field initializer, so nothing can hand it another environment.
- `ExecutableLocator` (and `SystemHostEnvironment`'s fallback) append `ToolCatalog.userBinFolders(os, home)` to every search. Those folders aren't part of the environment, so even an environment with a clean `PATH` finds `/opt/homebrew/bin/shogun` on a developer's Mac.

The e2e Gradle task (`:desktopApp:desktopE2eTest`) already isolates `user.home`, `HOME`, `PATH` (folders without a `shogun` launcher, plus `build/e2e/bin` first) and empties `SHELL`. The remaining leak is the usual install folders.

## Goals / Non-Goals

**Goals:**

- One object answers everything the app needs from the host: home, shell, variables, `PATH` and the usual install folders.
- The e2e tests hand `AppContainer` that object and pin "installed" and "missing" for `shogun` on any machine.
- An e2e regression test for the false "not on the `PATH`" warning, through the real `SystemHostEnvironment`, a real (fake) login shell process and the real UI.

**Non-Goals:**

- Raising the desktop app's coverage in general (coverage-desktop does that).
- Injecting other host facts read in `AppContainer` (`jpackage.app-path`, `java.home`, `user.name`): they don't decide whether `shogun` is found, and the e2e tests already run as an unpackaged app.
- Changing the CLI's behaviour.

## Decisions

### The usual install folders belong to the host environment

`HostEnvironment` gains `val userBinFolders: List<String>`. `SystemHostEnvironment` takes it as a constructor parameter defaulting to `ToolCatalog.userBinFolders(os, home)` and uses it for its fallback `PATH`. `ExecutableLocator`'s primary constructor gains a `userBinFolders` parameter with the same default, used by `find` and `userBinFolders()`; its secondary constructor passes `environment.userBinFolders`. So `CommandLineTool`, `LocalToolDetector`, editors, Herdr, Orca and GitHub's client all follow the environment without further changes.

Alternative considered: a separate `ToolSearchFolders` object passed to the locator. Rejected: it would be a second "host" object for the app to wire, which is what this change removes.

### `AppContainer(hostEnvironment: HostEnvironment = SystemHostEnvironment.forDesktopApp())`

A constructor parameter keeps `main.kt` unchanged and lets tests pass their own. The background thread that warms `path` up runs for whatever environment is given.

### E2e host environments are built by `E2eEnvironment`

- `hostEnvironment()`: a `SystemHostEnvironment` over the e2e task's own variables (already isolated by Gradle), the isolated home, no login shell, and `userBinFolders = [build/e2e/bin]`. Every e2e test builds `AppContainer(environment.hostEnvironment())`. `shogun` is found only when the test called `installFakeShogun()`.
- `dockHostEnvironment()` (macOS and Linux): variables with `PATH` `/usr/bin:/bin:/usr/sbin:/sbin` and `SHELL` pointing at a fake login shell script written under the e2e root, which prints `__SHOGUNAI_PATH__<bin>:<the task's PATH>__SHOGUNAI_PATH__`; the login shell runs through `ProcessBuilderShellCommandExecutor(path = null)`, as `forDesktopApp()` does, and `userBinFolders = [build/e2e/bin]`, standing in for `/opt/homebrew/bin`. The test also asserts the path source is `LOGIN_SHELL`, so it fails if the login shell stops being read. Git stays reachable because the login shell's `PATH` keeps the task's folders.

The Dock test is skipped on Windows (`assumeTrue`), where the app takes its own `PATH` and no login shell exists.

### Test split

- `TourE2eTest`: the existing full flow becomes the "missing" case (command step with "isn't installed", Skip, sample created, end tour); a new "installed" case fails if the Skip button appears before the open-agent step.
- `ClaudeHooksE2eTest`: the existing install/remove test (installed); a new "missing" test asserts the status text, the disabled install button and the "install the command-line tool first" hint.
- `DockLaunchE2eTest`: the hooks section reads available and the install button is enabled; the tour reaches the open-agent step without the command step.

## Risks / Trade-offs

- [Interface change in `core`] → `SystemHostEnvironment` is the only implementation; defaults keep every caller compiling and behaving the same.
- [Fake login shell on CI's Linux runner] → it's a `#!/bin/sh` script, as `DockLaunchedChildProcessTest` already runs on Linux and macOS CI.
- [More e2e tests lengthen `./gradlew test`] → two extra tour runs and two short hooks checks; each tour run creates one small sample repository.
