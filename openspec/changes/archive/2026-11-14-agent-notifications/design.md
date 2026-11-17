# Design

## Context

ShogunAi already knows the state of every agent it sees: `DetectRunningAgentsUseCase` merges Herdr's agents (`CliHerdrClient.listAgents`), Orca's and Claude Code's hook records (`AgentSessionRecord` in `FileAgentSessionStore`, read by `ReadAgentActivityUseCase`) into `RunningAgent(name, state, herdrPaneId, stateSource, …)` per worktree, with `AgentState { WORKING, BLOCKED, DONE, IDLE }`.
The desktop app reads them in `WorktreeBoard.checkProjects` on every check, but only while the window is shown: the loop waits for `windowActivity.isShown`, so nothing is read while the app lives in the menu bar, which is exactly when a notification is useful.
Ciro had a Claude Code hook that notified on every event and turned it off as noise. The agreed scope is the eight sub-bullets of the roadmap item, all of them in this change (Ciro chose "all, 1–8").

Findings with read-only commands (no Herdr or Orca writes):

- `herdr agent list` reports `"focused": true|false` for each agent's pane; `CliHerdrClient` doesn't read it yet.
- Herdr can notify by itself: `[ui.toast] delivery = "off" | "herdr" | "terminal" | "system"` in its `config.toml` (default `off`). `terminal` and `system` are desktop notifications; `herdr` is an in-app toast. `[ui.sound]` only plays sounds.
- Orca keeps its settings in `profiles/<profile>/profile-state.db` (SQLite) under its app data folder, table `profile_state_documents`, row `domain = 'settings'`, a JSON payload with `notifications: { enabled, agentNeedsInput, agentTaskComplete, terminalBell, suppressWhenFocused, … }`. Orca's code defaults `agentNeedsInput` to `agentTaskComplete` when absent. There is no CLI to read them.
- On this Mac (JBR 21), `java.awt.TrayIcon.displayMessage` posts through the deprecated `NSUserNotificationCenter`; a test notification was not shown, and `CTrayIcon` has no Java callback for a click anyway. Compose's `TrayState.sendNotification` is built on it.

## Goals / Non-Goals

**Goals:**

- Notify only on working → blocked or working → done, after a grace period, grouped, silent for a focused Herdr pane and for a runner that already notifies.
- Clicking opens ShogunAi on the worktree, on every system where the platform lets ShogunAi know about the click.
- Two switches, Blocked (on) and Done (off), shared by the app and the CLI.
- The same rules from the CLI while the desktop app isn't running, fed by Claude Code's hooks.
- All the deciding logic pure, in `core`, tested with fakes; the system mechanisms behind one port, chosen by `osName`.

**Non-Goals:**

- Floating popups, mascots, ShogunAi's own sounds, a notification history or per-project settings.
- Notifying from the CLI for agents other than Claude (only Claude Code has hooks), or for Herdr/Orca agents while the app is closed.
- Click handling from the CLI on Linux and any CLI notification on Windows (see Decisions).

## Decisions

### A pure tracker in core

`AgentNotificationTracker` lives in `domain/usecase` as a pure function over an immutable state, so the app keeps it in memory and the CLI persists it:

```kotlin
fun advance(state: TrackerState, observed: List<ObservedAgent>, now: Instant, policy: NotificationPolicy): TrackerStep
data class TrackerStep(val state: TrackerState, val notification: AgentNotification?, val nextCheckAt: Instant?)
```

- `ObservedAgent(key, ref: NotifiedWorktree(projectId, projectName, worktreePath, worktreeName), agentName, state, runner, focused)`. The key is the worktree path, the agent name and, when known, the Herdr pane or Claude session, so two Claudes in one worktree are two agents.
- An agent observed for the first time only records its state: starting the app or the first hook never notifies.
- Last known `WORKING` and now `BLOCKED` or `DONE` makes the agent pending since `now`. Any other change records the state and notifies nothing (blocked → done, idle → done, done → blocked).
- A pending agent seen `WORKING` again, or no longer seen, is dropped. Seen `BLOCKED`/`DONE` at or after `since + grace`, it is confirmed with its latest state; a blocked → done change while pending keeps the original `since`.
- A confirmed agent is notified when the policy allows its state (`blocked`/`done` switches), it isn't `focused`, and its runner doesn't notify that state itself; otherwise it's confirmed silently. Either way it isn't pending any more.
- Everything confirmed in one `advance` makes one `AgentNotification(items)`, blocked items first; `nextCheckAt` is the earliest `since + grace` still pending, so the caller knows when to look again.

The grace is 8 seconds: chained turns and subagents go back to work within a couple of seconds, and 8 s is short next to how long a blocked agent waits.
The confirmation needs a fresh observation, not only the clock, because the detection runs every 3 to 60 seconds: the caller schedules one extra detection at `nextCheckAt`.

### What the notification says and where a click goes

`AgentNotificationText` in `core` builds the title and body in English or Spanish, from the language preference, so the app and the CLI say the same:

- One item: "Claude needs you" or "Claude is done"; body "feature/login · api" (worktree's branch, or folder name when detached, and project).
- Several: "3 agents need you", "2 agents need you, 1 is done" or "2 agents are done"; body one line per item, "Claude · feature/login · api".

The click target is the first item's worktree. One notification for several agents can only open one place; the first is the most urgent and the others are listed in the body.

### Settings

`NotificationPreferences(blocked = true, done = false)` is stored in `~/.shogunai/settings.json` (`notifyBlocked`, `notifyDone`, through `JsonSettingsFile` and a `NotificationPreferenceRepository`), the file the app's other preferences already use, so the CLI reads the same switches. The Settings window gets a "Notifications" section with two switches, nothing else.
When both are off, the app doesn't keep detecting agents while hidden and the hook doesn't schedule anything.

### Silent when the runner already notifies

`RunnerNotifications` says which runner notifies which state, read by two adapters behind `RunnerNotificationSource`:

- Herdr notifies both states when `[ui.toast] delivery` is `terminal` or `system` in its `config.toml` (`HerdrConfigFile`, the same file and path rules `worktreesRoot` already uses). `herdr` (in-app toast) and `off` don't count: they don't reach the desktop.
- Orca notifies blocked when `notifications.enabled && (agentNeedsInput ?: agentTaskComplete)` and done when `enabled && agentTaskComplete`, read with `sqlite3 -readonly` (argv, no shell) from the active profile's `profile-state.db` (`activeProfileId` of `orca-profile-index.json`, `local-default` otherwise). Orca holds that database with an exclusive lock, so a normal read-only open fails with "unable to open database file"; the URI `file:…?mode=ro&immutable=1` reads it without locking. A read that races a write fails or returns garbage, which falls back like any unreadable case. Missing fields take Orca's defaults (enabled, both on). When the database can't be read (no `sqlite3`, as on Windows, or a changed schema), ShogunAi assumes Orca's defaults and stays silent for Orca's agents: a missed duplicate is worse than relying on Orca.
- Values are cached for 60 seconds.

An agent's runner is Herdr when Herdr reports its pane and Orca when Orca reports its state, as the agent indicator already decides (`agent-indicator`). Orca's `suppressWhenFocused` and `notifyHiddenWorkspaces` aren't modelled: when Orca is set to notify, ShogunAi leaves Orca's agents to it.
Reading Orca's private database is fragile by nature; it's isolated in one adapter with fixtures copied from the real payload, and a failure falls back to silence for Orca's agents only.

### Focus in Herdr

`HerdrAgent` gains `focused`, read from `agent list`, and `RunningAgent` gains `focusedInRunner`, carried by `DetectRunningAgentsUseCase`. The tracker drops an agent whose pane is focused at confirmation time. Orca doesn't report focus, so only Herdr's counts.

### One port, an adapter per system

`SystemNotifier { fun isAvailable(): Boolean; fun show(notification: SystemNotification, onClick: (() -> Unit)?) }` in `domain/io`, with `SystemNotification(title, body, click: OpenWorktreeTarget)`. `systemNotifier(osName, …)` picks:

- **macOS: a bundled helper app.** Ciro chose a native helper so the click reaches the right worktree. `ShogunAi Notifications.app` (bundle id `app.luxion.shogunai.notifications`, `LSUIElement`, ShogunAi's icon), one Swift file using `UNUserNotificationCenter`. `post --title … --body … --request <json> --requests-dir <dir> [--app <ShogunAi.app>]` asks for authorization the first time, adds the notification with the request JSON and paths in `userInfo`, and exits once it's added. A click relaunches the helper through Launch Services; its delegate sets `createdAt` to the current time in the request JSON, writes it atomically into ShogunAi's request folder as `FileAppRequestChannel` names files, opens `--app` when given (or nothing in development, where the running app polls the folder) and exits. Kotlin owns every field of the request except the timestamp, so the format lives in one place. The click therefore goes through the same path as `shogun app`, with the same guard for unsaved forms.
- **Linux: `notify-send`.** The app runs `notify-send --app-name=ShogunAi --action=default=<Open> --wait -- <title> <body>` and reads its output on a background thread; `default` means a click, handled in process. Without `notify-send`, notifications aren't available.
- **Windows: the system tray.** A `java.awt.TrayIcon` of its own, added when the first notification is shown and kept, with `displayMessage` and an `ActionListener`, which Windows calls when the notification is clicked. Compose's tray icon doesn't expose its `TrayIcon` nor a click on its notifications, so with the tray icon on there are two icons once a notification has been shown; checked by hand on Windows only, behind the `TrayBalloon` port so the rest is unit tested.

The helper is built by a Gradle task with `swiftc` (`-target <arch>-apple-macos12`, the runner's architecture, as the DMGs are built per architecture) into `desktopApp/build/notifier/macos/ShogunAi Notifications.app`, wired into Compose's `appResourcesRootDir` so it lands in `Contents/app/resources/` of the app and is also there for `./gradlew :desktopApp:run` (`compose.application.resources.dir`). It is signed before packaging: with the Developer ID identity and the hardened runtime when `macSigningIdentity` is set, ad hoc otherwise. Off macOS the task is skipped. Its Swift has no Kotlin coverage; the Kotlin side (arguments, request JSON, locating the helper) is unit tested, and the helper itself is checked by hand and by `codesign --verify --deep --strict` on the packaged app.

Rejected: AWT tray notifications on macOS (not shown, no click), `osascript display notification` (attributed to Script Editor, a click opens it), `terminal-notifier` (an external dependency), calling `UNUserNotificationCenter` in process through JNA (Objective-C blocks and delegates from the JVM, and no bundle in development).

### The desktop app's watch

`AgentNotificationWatch` (desktop) holds the tracker state and is fed by `WorktreeBoard` after every agent detection, with the projects' names and the worktrees' branches the board already has. When a step returns `nextCheckAt`, the watch asks the board for one agents-only detection at that time. A notification is shown through `SystemNotifier`; its click goes through the app request handler as an `open-worktree` request, so it shows the window and reveals the worktree in "All worktrees".

While the window is hidden and either switch is on, the board's loop runs agents-only checks at the cadence's unfocused interval (15 s on Balanced), with no Git metadata reads, device refreshes or Orca syncs; the window being shown again resumes the normal checks. With both switches off it stays as today. Agents are detected for every project whose worktrees the board has listed in the session; the app lists every saved project at start when notifications are on, so a project that was never opened is covered too.

### The CLI path, without the desktop app

`shogun hook agent-state` already writes Claude's state per session. When the desktop app's lock isn't held, a switch is on, and the session's previous record was `WORKING` and the new one is `BLOCKED` or `DONE`, the hook adds the session to `~/.shogunai/notifications/pending.json` (versioned JSON, under a file lock, with the tracker's state for those sessions) and starts a detached `shogun hook notify-pending` before returning, so Claude Code is never delayed.

`notify-pending` is started as the same JVM (`java.home`/`bin/java`, `java.class.path`, the CLI main class), which works for the standalone zip and for the CLI inside the app without knowing the launcher's path. It sleeps until the earliest due time, then, under the file lock: if the app is running now, it clears the pending list (the app takes over); otherwise it rebuilds each pending session's observation from the session store (effective state as `ReadAgentActivityUseCase` computes it, so a resumed transcript counts as working), Herdr's `agent list` for focus when Herdr runs, and the runner settings, calls the same `advance`, shows one notification for what's confirmed and saves what's still pending. Several sessions due together make one notification; another `notify-pending` that finds nothing due exits.

The CLI's notifier is the same port: on macOS the helper is found inside the app the CLI runs from (`java.home` inside `ShogunAi.app`), else in `/Applications` or `~/Applications`; without the app installed there's nothing to post with. On Linux it runs `notify-send` without `--wait`, so there is no click. On Windows the tray needs a living process, so the CLI doesn't notify.

### Opening a worktree by request

`AppRequest` gains the action `open-worktree` with `projectId` and `worktreePath`. It stays version 1: older apps don't understand the action and delete the request, as for any unknown action. `shogun app <path>` sends it when `<path>` is inside one of the project's worktrees other than the main checkout, and `open-project` otherwise, so it also serves people, not only clicks.

### A badge for the agent and its state

Asked for by Ciro after seeing the first real notification. macOS always shows the posting app's icon on the left, whatever the notification, so the per-agent image goes as a `UNNotificationAttachment`, shown as a thumbnail and enlarged when the notification is expanded; on Linux it is `notify-send`'s `--icon`; Windows' tray offers only fixed message types.

`RenderedNotificationImages` (behind the `NotificationImages` port) draws a 256 px rounded square with `java.awt` in headless mode: amber with a white "!" mark for needs you, green with a check mark for done, and the agent's initial in white, or the number of agents (up to 9) for a grouped notification, coloured by its first item. It writes each badge once into `~/.shogunai/notifications/images` (`needs-you-c.png`, `done-3.png`…) and reuses it. Vendors' logos (Claude, Codex, Gemini) are not used: they are other companies' marks. Drawing can fail where Java has no fonts (`UnsatisfiedLinkError` on a minimal Linux), so any failure leaves the notification without an image. The helper copies the image to a temporary file before attaching it, because macOS moves an attachment's file into its own store.

### Keeping the board under its length cap

`WorktreeBoard.kt` is on the file length exception list at its cap. The watch loop moved to `BoardCheckLoop` and the device refresh times to `DeviceRefreshSchedule`, both new files, so the board can call the notification watch without growing; its cap is lowered to its new length.

### Fitting the capped files after merging develop

`develop` lowered every file's length cap to its length and forbade raising them, so the files this change touches keep their base caps and the new code lives in files of its own: the CLI's notification hook and `notify-pending` in `AgentNotificationHook.kt`, the request and message of `shogun app` in `AppTargets.kt` (with the two Herdr and Orca close-failure texts moved there too), and the desktop app's notification wiring in `DesktopNotificationServices.kt`, which builds a `NotificationServices` bundle and the watch so `AppContainer.kt` holds one line for it. Two single-use aliases of `AppContainer.kt` (`readDesktopTurn`, `releaseDesktopTurn`) were inlined to keep it at its cap.

### Coverage

`core`'s maximum of missed branches goes from 18 to 19: adding `notifyBlocked` and `notifyDone` to the `@Serializable` `Settings` adds one more of the deserializer checks kotlinx.serialization generates for classes whose fields all have defaults, which no input reaches. The pending-notification store is written and read by hand instead of through `@Serializable` classes, so it adds none. The board's length cap is lowered to its new length.

### The click from the desktop app goes through the request folder

The watch's click handler writes the `open-worktree` request into `~/.shogunai/app-requests` instead of navigating directly: `notify-send`'s and AWT's callbacks arrive on background threads, and the request poller already serves requests on the UI thread with the guard for unsaved forms. On macOS the helper writes the same request, so every click takes one path.

## Risks / Trade-offs

- [Compose re-signs files inside the bundle and could break the helper's nested signature, failing notarization] → The helper is signed before packaging and the packaged app is checked with `codesign --verify --deep --strict` locally; before release, the macOS installer workflow is run on the branch with Ciro's agreement.
- [The first notification on macOS asks for permission, and a denial silences them] → The helper asks once; macOS keeps the choice in System Settings, which the architecture docs and the guide mention. The notification that triggers the question is lost when the user takes longer than the helper's post to answer; the next one shows.
- [Orca's database format can change with any Orca update] → One adapter with fixtures; any failure falls back to silence for Orca's agents only.
- [A detached `notify-pending` could be killed with Claude Code's hook process group] → It's started in its own process; checked by hand with a real Claude session, and the next app start clears stale pending entries.
- [Agents-only checks while hidden cost a process scan every 15 seconds] → Only with a switch on; the scan is the one the visible window already runs, without Git or device reads.
- [Polling can miss a working → blocked → working that happens between two checks] → That is the grace period's intent; a long block is always seen.
- [With the window hidden the app reads agents every 15 to 60 seconds depending on the refresh cadence, so a turn shorter than that goes from idle to done without being seen working, and doesn't notify] → Accepted with Ciro during validation: the rule stays "only from working". Claude Code's hooks see every turn, so the CLI path isn't affected; reading Herdr's `completion_seq` would catch them in the app and is left as a follow-up.

## Migration Plan

New settings default to Blocked on and Done off; older apps ignore the new fields in `settings.json`, the new request action and the `notifications/` folder.

## Open Questions

None.
