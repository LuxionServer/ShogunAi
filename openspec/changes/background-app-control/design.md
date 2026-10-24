# Design

## Context

See proposal.md for the motivation. The spike of 2026-10-25 (macOS, ShogunAi on Compose Multiplatform 1.12.2 in throwaway worktrees) measured what the design relies on:

- Compose Hot Reload's MCP server (`./gradlew :<module>:hotMcpServer`, Compose Multiplatform 1.12+) finds the app through `<module>/build/run/main/main.pid` of its own worktree (it holds `pid` and `orchestration.port`). Two worktrees ran at once and each MCP drove only its own app. Semantic tree, click and `type_text` work with the app in the background without changing the frontmost app; they run semantics actions, not real input, so hit-testing, overlays and hover aren't exercised. Node ids change when the view changes.
- `take_screenshot` copies the screen region under the window: with the window covered it returned another session's terminal.
- `screencapture -x -o -l <windowId>`, with the id from `CGWindowListCopyWindowInfo` filtered by the app's pid and layer 0, returns the window's current content even covered, nearly off screen or with the app hidden.
- `hotRun` and the MCP's `restart` activate the app for about a second; with `-Dapple.awt.UIElement=true` the app never came to the front.
- Hiding the application (the AX hidden attribute, like Cmd+H) keeps the MCP and the capture working; unhiding didn't steal focus. Minimizing breaks the MCP. Moving the window off screen is clamped by macOS and no public API moves it to another Space.
- Key events posted to the app's pid never arrive: AWT drops keys for a window that isn't active.
- ShogunAi keeps its data under `user.home` (`~/.shogunai`, with an `app.lock`), so an instance next to the person's needs its own home; the spike set `user.home` on the `hotRun` task.

Existing pieces this builds on: providers declared in `.shogunai.json` (`emulator`, `simulator`, `gradle`…), per-worktree use cases such as `WorktreeEmulatorUseCases`, the KMP init script that ShogunAi already writes and passes to Gradle (`FileKmpTestWiring`), session records kept in the worktree's Git administrative folder, `RemoveWorktreeUseCase` for every removal path, and the session-start context.

## Goals / Non-Goals

**Goals:**
- Any Compose Desktop app declared by module, not only ShogunAi, without changes to the repository's build scripts.
- Nothing visible to the person and no focus change at any point of start, hide, capture or stop.
- No new native code shipped with ShogunAi: only tools that come with macOS, run as argv.

**Non-Goals:**
- Wrapping, proxying or replacing the Hot Reload MCP server; agents configure it themselves.
- Keyboard input, native menus, the menu bar item, system dialogs, hover and hit-testing: they stay for the planned desktop turn.
- Windows and Linux: the commands report "not supported". How hiding and window capture map to them is a roadmap item.
- Showing headless apps in the desktop app's worktree views (a follow-up).
- Compose Desktop apps that aren't launched through Compose Hot Reload, or JVM desktop apps without Compose.

## Decisions

### A `desktopApp` provider declared by module
`"providers": { "desktopApp": { "module": ":desktopApp", "isolatedHome": true } }`. The module is enough to derive both the run task (`<module>:hotRun`) and where Hot Reload writes `main.pid` (the module's `build/run/main/`), which the MCP also reads. Alternatives: a free-form `run` command (more flexible but `shogun` would lose the `main.pid` contract and need its own process discovery), or hard-coding ShogunAi (rejected by the user: the point is every Compose Desktop app).

Running the declared task needs no new approval in `repository-trust`: nothing runs at worktree creation, and `shogun headless start` is invoked explicitly by the agent inside the worktree, which is equivalent to the agent running `./gradlew` there itself, as it already does. The module path is validated as a Gradle project path (`:a:b`), so it can't smuggle extra arguments into the argv.

### Launch: Hot Reload's run task with an init script
`shogun` runs `./gradlew -I <init script> -Pshogunai.headless.home=<home> <module>:hotRun` (argv, from the worktree, detached with stdin closed, stdout and stderr to the log). The init script is written by ShogunAi next to the KMP one and configures the run task of that module to add `-Dapple.awt.UIElement=true` and, when `isolatedHome` is on, `-Duser.home=<home>`. This keeps repositories' build scripts untouched and reaches the JVM of the app, not Gradle's daemon. Alternatives: `JAVA_TOOL_OPTIONS` (rejected: it also reaches Gradle and its daemon, and `user.home` there moves Gradle's own caches), or asking repositories to add the properties to their build (rejected: per-repository work and it would leak into the person's own `hotRun`).

If Hot Reload in Compose Multiplatform 1.12 provides an asynchronous run task that returns once the app is up, the first implementation task decides whether to use it instead of keeping a detached Gradle process; the behaviour in the specs is the same either way.

### Waiting, hiding and window lookup through JavaScript for Automation
After launching, `shogun` polls for `main.pid` with a live pid, then for a layer-0 window owned by that pid, then hides the application. Window lookup and hiding run in one `osascript -l JavaScript` helper that uses the Objective-C bridge (`CGWindowListCopyWindowInfo`, `NSRunningApplication.runningApplicationWithProcessIdentifier(pid).hide`), invoked as argv with the pid as an argument, never a script built by string concatenation with untrusted text. `NSRunningApplication.hide` is preferred over the AX attribute the spike used because it may need no Accessibility permission; the first task verifies it keeps the MCP working and falls back to AX (`kAXHiddenAttribute`) if not. Alternatives: a small Swift helper binary (rejected: code signing and packaging for every distribution), JNA calls from the JVM (rejected: a new native dependency in `core` and the CLI). Minimizing and moving the window off screen are ruled out by the spike.

The pid used for windows and capture is the app's JVM pid from `main.pid`, not Gradle's. Between the window appearing and the hide there is a brief moment where the window can show behind the frontmost app; it never takes focus.

### Capture by window id with `screencapture`
`screencapture -x -o -l <windowId> <file>`: no sound, no shadow, only that window. The window id is looked up on every capture, since a restart from the MCP creates a new window. A PNG that macOS returns without permission is blank or shows only the desktop background; `shogun` checks the Screen Recording permission first (`CGPreflightScreenCaptureAccess` through the same helper) and fails with the error the spec describes instead of writing a misleading file. Default output: a new file in the worktree's Git administrative folder (`shogunai/headless/captures/`), so captures never show in `git status`.

### State in the worktree's Git administrative folder
Under `<git dir>/shogunai/headless/`: `home/` (the isolated home), `launch.log`, `launch.json` (the Gradle launcher's pid, start time) and `captures/`. Like session records, this is invisible to `git status`, not copied to other worktrees and deleted with the worktree, which gives the "removed with the worktree" behaviour of the isolated home for free. The app's own pid is always read from `main.pid` and checked alive, so a crash outside ShogunAi is reported as stopped.

### Stop
Send SIGTERM to the app's pid and to the Gradle launcher, wait briefly, then SIGKILL what remains. Every removal path already goes through `RemoveWorktreeUseCase`, which gets a step that stops the worktree's headless app before deleting the folder and reports a failure as a warning.

### Ports and use cases
New ports in `core`: `HeadlessAppLauncher` (launch, stop) and `AppWindowService` (find window, hide, show, capture, permission checks), with macOS implementations in `infrastructure` and a not-supported implementation elsewhere. A `WorktreeHeadlessAppUseCases` mirrors `WorktreeEmulatorUseCases`, returning `Result<T>` with a sealed `HeadlessAppError` (not supported, provider missing, launch exited with log tail, window timeout, permission missing, not running). Tests use fakes of both ports; the macOS implementations get a manual verification task on a real app.

### ShogunAi as the first user
ShogunAi's own `.shogunai.json` declares `"desktopApp": { "module": ":desktopApp" }`, and its repository declares the Hot Reload MCP server for agents (project `.mcp.json` running `./gradlew :desktopApp:hotMcpServer`), so every worktree's agent gets an MCP bound to its own app. ShogunAi's `focusMainWindow` (`toFront`, `requestFocus`, `requestAppForeground`) is checked under `apple.awt.UIElement`; if it still activates the app, it is skipped when the app runs as a UI element.

## Risks / Trade-offs

- [Hot Reload's MCP and run task are experimental and may change between Compose versions] → `shogun` depends only on the run task's name and `main.pid`; the provider docs name the Compose version tested, and a mismatch fails the start with the launch log.
- [The MCP's `restart` relaunches the app; it may not keep the init script's properties and would then show it and take focus] → verified in the implementation; if it does, the session-start context tells agents to use `shogun headless stop` and `start` instead of `restart`.
- [`NSRunningApplication.hide` may not apply to a UI element app, or need a permission] → fall back to the AX attribute, report the missing permission as the spec says.
- [Semantics actions don't test what a real click does] → documented in the context and docs; hit-testing and hover checks stay for the desktop turn.
- [The window can be visible for a fraction of a second before it is hidden] → it never takes focus; accepted. A follow-up could start the window off screen before hiding.
- [Permissions are granted to the terminal or agent host, not to ShogunAi] → errors name the application to grant them to; docs cover onboarding.
- [`user.home` doesn't change `HOME` for child processes] → intended: tools the app runs (Git) keep the person's configuration; only the app's own JVM data is isolated.

## Migration Plan

Prerequisite: a separate change upgrades ShogunAi to Compose Multiplatform 1.12 and is merged into `develop` before this change's implementation starts. The provider is opt-in, so repositories that don't declare it see no change; no data migration. Rollback: remove the provider from `.shogunai.json`.

## Open Questions

- Whether Compose 1.12's Hot Reload has an asynchronous run task, and whether its MCP's `restart` keeps the launch's system properties (answered by the first implementation task without changing the specs).
- Whether cmp-bridge's posting of AWT events to the app's own event queue gets past AWT's focus filter, which would close the keyboard gap without the desktop; it is a separate exploration, as is a feature request to JetBrains for a key tool in the MCP.
