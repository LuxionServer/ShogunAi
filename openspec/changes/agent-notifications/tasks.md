# Tasks

## 1. Core model and tracker

- [ ] 1.1 Add `focused` to `HerdrAgent` (read from `agent list` in `CliHerdrClient`) and `focusedInRunner` to `RunningAgent`, carried by `DetectRunningAgentsUseCase`; tests with fixtures copied from `herdr agent list`.
- [ ] 1.2 Add the notification models (`ObservedAgent`, `NotifiedWorktree`, `AgentNotification`, `NotificationPolicy`, `RunnerNotifications`, `TrackerState`, `TrackerStep`) and the pure `advance` of `AgentNotificationTracker`, with tests for every rule in the design: first observation, working → blocked/done, other changes, grace, drop on working or vanished, blocked → done while pending, focus, switches, runner silencing, grouping and `nextCheckAt`.
- [ ] 1.3 Add `AgentNotificationText` (English and Spanish) for one and several items, and the click target; tests.
- [ ] 1.4 Add `NotificationPreferences` to `settings.json` (`notifyBlocked` default true, `notifyDone` default false) with `NotificationPreferenceRepository` and its JSON implementation; tests.
- [ ] 1.5 Add the `open-worktree` action to `AppRequest` (`projectId`, `worktreePath`), still version 1; tests for parsing, unknown actions and older payloads.

## 2. Runner settings

- [ ] 2.1 Read Herdr's `[ui.toast] delivery` in `HerdrConfigFile` and map `terminal`/`system` to "notifies both states"; tests with TOML fixtures (missing table, comments, quotes, other values).
- [ ] 2.2 Add `OrcaNotificationSettings` reading the active profile's `profile-state.db` with `sqlite3 -readonly` (argv) and parsing `notifications`; defaults and unreadable cases fall back to Orca's defaults; app data folder per `osName`; tests with a fake executor and a payload fixture.
- [ ] 2.3 Add `RunnerNotificationSource` combining both, cached for 60 seconds; tests.

## 3. System notifiers

- [ ] 3.1 Add the `SystemNotifier` port, `SystemNotification` and `systemNotifier(osName, …)`; tests for the choice per system.
- [ ] 3.2 Add the macOS adapter: locate the helper (resources dir, app bundle from `java.home`, `/Applications`, `~/Applications`), build its `post` argv with the request JSON and requests folder; tests with a fake executor and fake file system.
- [ ] 3.3 Add the Linux adapter with `notify-send --action … --wait` (click read from output, in process) and the no-click variant for the CLI; tests with a fake executor.
- [ ] 3.4 Add the Windows adapter on `java.awt.TrayIcon` behind a small tray port so its logic is tested without AWT.

## 4. macOS helper

- [ ] 4.1 Write the Swift helper (`post` command, authorization, `userInfo`, delegate that stamps `createdAt`, writes the request atomically and opens the app) and its `Info.plist`.
- [ ] 4.2 Add the Gradle task that builds and signs `ShogunAi Notifications.app` on macOS (skipped elsewhere) and wire it into Compose's app resources for `run` and packaging.
- [ ] 4.3 Check by hand: a notification posted from `./gradlew :desktopApp:run` shows with ShogunAi's name and a click selects the worktree; `createDistributable` passes `codesign --verify --deep --strict`.

## 5. Desktop app

- [ ] 5.1 Add `AgentNotificationWatch` fed by the board after each detection, scheduling the extra detection at `nextCheckAt` and showing notifications through the notifier; tests with fakes.
- [ ] 5.2 Make the board's loop run agents-only checks while the window is hidden and a switch is on, and list every saved project at start when notifications are on; tests.
- [ ] 5.3 Serve `open-worktree` requests (show the window, reveal and select the worktree, fall back to the project) and send the click through them; tests.
- [ ] 5.4 Add the "Notifications" section to Settings with the two switches (English and Spanish strings); UI tests.

## 6. Command line

- [ ] 6.1 `shogun app <path>`: send `open-worktree` when the path is inside a linked worktree; tests.
- [ ] 6.2 Add the pending-notification store (`~/.shogunai/notifications/pending.json`, versioned, under a file lock); tests.
- [ ] 6.3 In `hook agent-state`, when the app isn't running, a switch is on and the session goes from working to blocked/done, add it to the pending store and start a detached `shogun hook notify-pending` with the same JVM; tests with a fake process starter.
- [ ] 6.4 Add the hidden `hook notify-pending` command: wait until due, drop everything if the app runs, rebuild observations from the session store, Herdr's focus and the runner settings, call `advance`, notify once and save the rest; tests.

## 7. Docs and validation

- [ ] 7.1 Add an architecture page on notifications (`docs/architecture/notifications.md` and `.es.md`, linked from `mkdocs.yml`), including macOS permission and the per-system limits.
- [ ] 7.2 Run `./gradlew test koverVerify`, cover any missed line or branch, and raise floors that pass a whole percentage.
- [ ] 7.3 Check the app with `shogun headless start`: the Settings section and an `open-worktree` request; check a real notification and click with Ciro on macOS, and the CLI path with a real Claude session and the app closed.
- [ ] 7.4 Archive the change and update the roadmap in both languages (remove the item and its sub-bullets, add any follow-ups).
