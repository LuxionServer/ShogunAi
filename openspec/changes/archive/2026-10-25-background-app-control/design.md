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

### A `desktop-app` provider declared by module
`"providers": { "desktop-app": { "module": ":desktopApp", "isolatedHome": true } }`. The module is enough to derive both the run task (`<module>:hotRun`) and where Hot Reload writes `main.pid` (the module's `build/run/main/`), which the MCP also reads. Alternatives: a free-form `run` command (more flexible but `shogun` would lose the `main.pid` contract and need its own process discovery), or hard-coding ShogunAi (rejected by the user: the point is every Compose Desktop app).

Running the declared task needs no new approval in `repository-trust`: nothing runs at worktree creation, and `shogun headless start` is invoked explicitly by the agent inside the worktree, which is equivalent to the agent running `./gradlew` there itself, as it already does. The module path is validated as a Gradle project path (`:a:b`), so it can't smuggle extra arguments into the argv.

### Launch: Hot Reload's asynchronous run task with an init script
`shogun` runs `./gradlew -I <init script> -Pshogunai.headless.home=<home> -Pcompose.reload.devToolsHeadless=true <module>:hotRunAsync` (argv, from the worktree, stdin closed, stdout and stderr to the log) and waits for Gradle to exit. The init script is written by ShogunAi next to the KMP one and configures the module's `hotRun` task (a `JavaExec`) to add `-Dapple.awt.UIElement=true`, `-Dshogunai.headless=true` and, when `isolatedHome` is on, `-Duser.home=<home>`. This keeps repositories' build scripts untouched and reaches the JVM of the app, not Gradle's daemon. Alternatives: `JAVA_TOOL_OPTIONS` (rejected: it also reaches Gradle and its daemon, and `user.home` there moves Gradle's own caches), or asking repositories to add the properties to their build (rejected: per-repository work and it would leak into the person's own `hotRun`).

Verified on Compose Multiplatform 1.12.2 (Hot Reload 1.2.0, task 1.1):
- `hotRunAsync` (`ComposeHotAsyncRun`, not a `JavaExec`) launches the app from the `main.argfile` that `hotRunArgfile` writes from `hotRun`'s settings, prints `Started '<main class>' in background (<pid>)` and returns; the app's parent is `launchd`, so no Gradle process has to be kept or stopped. A build failure makes Gradle exit non-zero, which is the "launch exits early" case; the app's own output goes to `<module>/build/run/main/hotRun.stdout.txt` and `hotRun.stderr.txt`.
- Properties set on `hotRun` by the init script reach the app through the argfile (`-Dapple.awt.UIElement=true`, `-Duser.home=…` were in the process).
- The MCP's `restart` relaunches from the same argfile, so it keeps the properties and doesn't take focus (frontmost app sampled every second stayed unchanged), but the new process shows its window again and gets a new pid in `main.pid`. `shogun headless start` on a running app hides it again; the session-start context says so.
- Hot Reload's Dev Tools run as a child process with a small window of their own (36×200 pt) that stays visible when the app is hidden. `-Pcompose.reload.devToolsHeadless=true` removes its windows, and the MCP keeps working.

`shogunai.headless=true` lets an app skip what it shows outside its window, which hiding can't reach: ShogunAi creates a menu bar status item, which would show up as a second icon next to the person's own instance.

### Waiting, hiding and window lookup through JavaScript for Automation
After launching, `shogun` polls for `main.pid` with a live pid, then for a layer-0 window owned by that pid, then hides the application. Window lookup and hiding run in one `osascript -l JavaScript` helper shipped as a resource, written to ShogunAi's folder and invoked as argv with the pid as an argument, never a script built by string concatenation. It uses the Objective-C bridge: `CGWindowListCopyWindowInfo` for windows, `AXUIElementSetAttributeValue(AXUIElementCreateApplication(pid), "AXHidden", true)` to hide, `AXIsProcessTrusted` and `CGPreflightScreenCaptureAccess` (bound with `ObjC.bindFunction`) for permissions. Alternatives: a small Swift helper binary (rejected: code signing and packaging for every distribution), JNA calls from the JVM (rejected: a new native dependency in `core` and the CLI). Minimizing and moving the window off screen are ruled out by the spike.

Verified (task 1.2): `NSRunningApplication.hide` returns `false` and does nothing for an app whose activation policy is "accessory", which `apple.awt.UIElement` gives, so hiding uses the AX attribute and needs the Accessibility permission. `NSRunningApplication.isHidden` stays `false` after an AX hide (it is cached), so "hidden" is read from the windows instead: a hidden app's layer-0 windows lack `kCGWindowIsOnscreen`. Hiding before the window exists has no effect on the window created afterwards, which is why the start waits for it. With the app hidden, the MCP's tree and click worked and didn't show it again, and `screencapture -l` returned its current content.

The pid used for windows and capture is the app's JVM pid, not Gradle's. On a start it is taken from the line `hotRunAsync` prints (`Started '<main class>' in background (<pid>)`), because `main.pid` can still be missing for a moment after Gradle returns (seen in the integration check); afterwards it is read from `main.pid`, which the MCP's `restart` updates. Between the window appearing and the hide there is a brief moment where the window can show behind the frontmost app; it never takes focus.

### Capture by window id with `screencapture`
`screencapture -x -o -l <windowId> <file>`: no sound, no shadow, only that window. The window id is looked up on every capture, since a restart from the MCP creates a new window. A PNG that macOS returns without permission is blank or shows only the desktop background; `shogun` checks the Screen Recording permission first (`CGPreflightScreenCaptureAccess` through the same helper) and fails with the error the spec describes instead of writing a misleading file. Default output: a new file in the worktree's Git administrative folder (`shogunai/headless/captures/`), so captures never show in `git status`.

### State in the worktree's Git administrative folder
Under `<git dir>/shogunai/headless/`: `home/` (the isolated home), `launch.log` (Gradle's output of the last start) and `captures/`. Like session records, this is invisible to `git status`, not copied to other worktrees and deleted with the worktree, which gives the "removed with the worktree" behaviour of the isolated home for free. The app's own pid is always read from `main.pid` and checked alive, so a crash outside ShogunAi is reported as stopped.

### Stop
Send SIGTERM to the app's pid from `main.pid` and its child processes (Hot Reload's Dev Tools), wait briefly, then SIGKILL what remains; there is no Gradle process left by `hotRunAsync`. A SIGTERM to the app ended both in the verification. Every removal path already goes through `RemoveWorktreeUseCase`, which gets a step that stops the worktree's headless app before deleting the folder and reports a failure as a warning.

### Reading the provider from the worktree
The provider is read from the worktree's own `.shogunai.json`, not the base repository's like the emulator, so a branch that adds or changes the declaration can run its app before it is merged; the session-start rule follows the same file. The commands work in any worktree or checkout, registered as a project or not.

### Session-start rule
The rule joins the session-start context. To keep that context within ten lines with every provider declared, the emulator rule and the shared adb server rule are merged into one line; their text is unchanged.

### Removal from the desktop app
The desktop app's removal and cleanup stop the headless app too, but a failure to stop it is only reported by the command line: the desktop app has no notice for it yet (a follow-up, with showing headless apps in its views).

### Ports and use cases
New ports in `core`: `HeadlessAppLauncher` (launch, stop) and `AppWindowService` (find window, hide, show, capture, permission checks), with macOS implementations in `infrastructure` and a not-supported implementation elsewhere. A `WorktreeHeadlessAppUseCases` mirrors `WorktreeEmulatorUseCases`, returning `Result<T>` with the errors as new `WorktreeError` cases, like every other use case (not supported, provider missing, launch failed or app exited with the log's tail, window timeout, permission missing, not running, stop failed). Tests use fakes of both ports; the macOS implementations get a manual verification task on a real app.

### ShogunAi as the first user
ShogunAi's own `.shogunai.json` declares `"desktop-app": { "module": ":desktopApp" }`, and its repository declares the Hot Reload MCP server for agents (project `.mcp.json` running `./gradlew :desktopApp:hotMcpServer`), so every worktree's agent gets an MCP bound to its own app. ShogunAi's `focusMainWindow` (`toFront`, `requestFocus`, `requestAppForeground`) doesn't activate the app under `apple.awt.UIElement` (task 1.3: the frontmost app stayed unchanged through a launch), so it is left as is. With `shogunai.headless=true` ShogunAi doesn't create its menu bar status item.

## Risks / Trade-offs

- [Hot Reload's MCP and run task are experimental and may change between Compose versions] → `shogun` depends only on the run task's name and `main.pid`; the provider docs name the Compose version tested, and a mismatch fails the start with the launch log.
- [The MCP's `restart` shows the window again] → the session-start context tells agents to run `shogun headless start` after a `restart` to hide it again.
- [Hiding needs the Accessibility permission for the application running `shogun`] → a denied hide leaves the app running visible in the background with a warning naming the permission.
- [The person can show a hidden app again from the desktop (seen once in the integration check, with no ShogunAi command involved)] → `shogun headless status` reports it as visible and `shogun headless start` hides it again.
- [Things an app shows outside its window, such as a menu bar status item, can't be hidden from outside] → `shogunai.headless=true` lets the app skip them; documented for other repositories.
- [Semantics actions don't test what a real click does] → documented in the context and docs; hit-testing and hover checks stay for the desktop turn.
- [The window can be visible for a fraction of a second before it is hidden] → it never takes focus; accepted. A follow-up could start the window off screen before hiding.
- [Permissions are granted to the terminal or agent host, not to ShogunAi] → errors name the application to grant them to; docs cover onboarding.
- [`user.home` doesn't change `HOME` for child processes] → intended: tools the app runs (Git) keep the person's configuration; only the app's own JVM data is isolated.

## Migration Plan

Prerequisite: a separate change upgrades ShogunAi to Compose Multiplatform 1.12 and is merged into `develop` before this change's implementation starts. The provider is opt-in, so repositories that don't declare it see no change; no data migration. Rollback: remove the provider from `.shogunai.json`.

## Open Questions

- Whether cmp-bridge's posting of AWT events to the app's own event queue gets past AWT's focus filter, which would close the keyboard gap without the desktop; it is a separate exploration, as is a feature request to JetBrains for a key tool in the MCP.
