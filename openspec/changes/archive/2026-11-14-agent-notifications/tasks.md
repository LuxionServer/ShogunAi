# Tasks

## 1. Core model and tracker

- [x] 1.1 Add `focused` to `HerdrAgent` (read from `agent list` in `CliHerdrClient`) and `focusedInRunner` to `RunningAgent`, carried by `DetectRunningAgentsUseCase`; tests with fixtures copied from `herdr agent list`.
- [x] 1.2 Add the notification models (`ObservedAgent`, `NotifiedWorktree`, `AgentNotification`, `NotificationPolicy`, `RunnerNotifications`, `TrackerState`, `TrackerStep`) and the pure `advance` of `AgentNotificationTracker`, with tests for every rule in the design: first observation, working → blocked/done, other changes, grace, drop on working or vanished, blocked → done while pending, focus, switches, runner silencing, grouping and `nextCheckAt`.
- [x] 1.3 Add `AgentNotificationText` (English and Spanish) for one and several items, and the click target; tests.
- [x] 1.4 Add `NotificationPreferences` to `settings.json` (`notifyBlocked` default true, `notifyDone` default false) with `NotificationPreferenceRepository` and its JSON implementation; tests.
- [x] 1.5 Add the `open-worktree` action to `AppRequest` (`projectId`, `worktreePath`), still version 1; tests for parsing, unknown actions and older payloads.

## 2. Runner settings

- [x] 2.1 Read Herdr's `[ui.toast] delivery` in `HerdrConfigFile` and map `terminal`/`system` to "notifies both states"; tests with TOML fixtures (missing table, comments, quotes, other values).
- [x] 2.2 Add `OrcaNotificationSettings` reading the active profile's `profile-state.db` with `sqlite3 -readonly` (argv) and parsing `notifications`; defaults and unreadable cases fall back to Orca's defaults; app data folder per `osName`; tests with a fake executor and a payload fixture.
- [x] 2.3 Add `RunnerNotificationSource` combining both, cached for 60 seconds; tests.

## 3. System notifiers

- [x] 3.1 Add the `SystemNotifier` port, `SystemNotification` and `systemNotifier(osName, …)`; tests for the choice per system.
- [x] 3.2 Add the macOS adapter: locate the helper (resources dir, app bundle from `java.home`, `/Applications`, `~/Applications`), build its `post` argv with the request JSON and requests folder; tests with a fake executor and fake file system.
- [x] 3.3 Add the Linux adapter with `notify-send --action … --wait` (click read from output, in process) and the no-click variant for the CLI; tests with a fake executor.
- [x] 3.4 Add the Windows adapter on `java.awt.TrayIcon` behind a small tray port so its logic is tested without AWT.

## 4. macOS helper

- [x] 4.1 Write the Swift helper (`post` command, authorization, `userInfo`, delegate that stamps `createdAt`, writes the request atomically and opens the app) and its `Info.plist`.
- [x] 4.2 Add the Gradle task that builds and signs `ShogunAi Notifications.app` on macOS (skipped elsewhere) and wire it into Compose's app resources for `run` and packaging.
- [x] 4.3 Check by hand: a notification posted from `./gradlew :desktopApp:run` shows with ShogunAi's name and a click selects the worktree; `createDistributable` passes `codesign --verify --deep --strict`.

## 5. Desktop app

- [x] 5.1 Add `AgentNotificationWatch` fed by the board after each detection, scheduling the extra detection at `nextCheckAt` and showing notifications through the notifier; tests with fakes.
- [x] 5.2 Make the board's loop run agents-only checks while the window is hidden and a switch is on, and list every saved project at start when notifications are on; tests.
- [x] 5.3 Serve `open-worktree` requests (show the window, reveal and select the worktree, fall back to the project) and send the click through them; tests.
- [x] 5.4 Add the "Notifications" section to Settings with the two switches (English and Spanish strings); UI tests.

## 6. Command line

- [x] 6.1 `shogun app <path>`: send `open-worktree` when the path is inside a linked worktree; tests.
- [x] 6.2 Add the pending-notification store (`~/.shogunai/notifications/pending.json`, versioned, under a file lock); tests.
- [x] 6.3 In `hook agent-state`, when the app isn't running, a switch is on and the session goes from working to blocked/done, add it to the pending store and start a detached `shogun hook notify-pending` with the same JVM; tests with a fake process starter.
- [x] 6.4 Add the hidden `hook notify-pending` command: wait until due, drop everything if the app runs, rebuild observations from the session store, Herdr's focus and the runner settings, call `advance`, notify once and save the rest; tests.

## 6b. Images

- [x] 6b.1 Add the `NotificationImages` port and `RenderedNotificationImages` (badge per state with the agent's initial or the count, cached on disk, failures leave it out); tests.
- [x] 6b.2 Pass the image to the macOS helper (`--image`, attached as a copy) and to `notify-send` (`--icon`), from the desktop watch and the CLI path; tests.

## 7. Docs and validation

- [x] 7.1 Add an architecture page on notifications (`docs/architecture/notifications.md` and `.es.md`, linked from `mkdocs.yml`), including macOS permission and the per-system limits.
- [x] 7.2 Run `./gradlew test koverVerify`, cover any missed line or branch, and raise floors that pass a whole percentage.
- [x] 7.3 Check the app with `shogun headless start`: the Settings section and an `open-worktree` request; check a real notification and click with Ciro on macOS (done, with the badge). The CLI path with the app closed is covered by unit tests only, not checked live: the installed app was running on this machine.
- [ ] 7.5 Before the next release, run the macOS installer workflow on this change (Developer ID signing and notarization with the helper inside), with Ciro's agreement.
- [x] 7.4 Archive the change and update the roadmap in both languages (remove the item and its sub-bullets, add any follow-ups).
