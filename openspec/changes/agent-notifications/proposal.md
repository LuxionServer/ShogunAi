# Proposal

## Why

Today the only signal that an agent needs the user, or has finished, is the worktree's row and the menu bar icon's menu, so an agent can sit blocked for a long time while the user works elsewhere.
A Claude Code hook that notified on every event was turned off because it was pure noise; ShogunAi already knows each agent's state from Herdr, Orca and Claude Code's hooks, so it can notify only when it matters.

## What Changes

- A native system notification when an agent changes from working to blocked or done, never on every event an agent reports.
- A short grace period: the change is dropped when the agent is working again within a few seconds, as between chained turns and subagents.
- No notification for an agent whose Herdr pane is focused.
- The notification names the agent, the project and the worktree; clicking it opens ShogunAi on that worktree.
- Changes confirmed together make one notification that lists them, not one each.
- Two switches in Settings: "Blocked" (on by default) and "Done" (off by default). No other configuration.
- No notification for an agent whose own runner already notifies that change: Herdr when its `[ui.toast] delivery` is `terminal` or `system`, Orca when its notifications for agents needing input or finishing are on.
- While the desktop app isn't running, Claude Code's `agent-state` hook sends the notification itself through the CLI, with the same rules.
- While notifications are on and the window is hidden, the app keeps detecting agents at the unfocused interval; it stops reading Git metadata as it does today.
- `shogun app <path>` opens the app on the worktree containing `<path>`, not only on its project, which is also what a click on a notification asks for.
- On macOS, a small helper app bundled inside ShogunAi posts the notifications through `UNUserNotificationCenter`, because the JVM's tray notifications are no longer shown and don't report clicks. Linux uses `notify-send`, Windows the system tray's notifications.

Out of scope: floating popups, mascots or sounds of ShogunAi's own; the worktree's row and the menu bar menu stay the traffic light.

## Capabilities

### New Capabilities

- `agent-notifications`: when and how ShogunAi notifies that an agent became blocked or done: transitions, grace period, focus and runner suppression, grouping, the click, the Settings switches, the CLI path without the desktop app and the notification mechanism of each system.

### Modified Capabilities

- `worktree-auto-refresh`: agents keep being detected while the window is hidden when notifications are on.
- `desktop-app-control`: `shogun app <path>` inside a worktree opens the app on that worktree.

## Impact

- `core`: a pure transition tracker with its settings and models, a `SystemNotifier` port and its per-system adapters, readers for Herdr's and Orca's own notification settings, Herdr's `focused` flag in `HerdrAgent`/`RunningAgent`, a pending-notification store for the CLI path, and an `open-worktree` app request.
- `cli`: `shogun hook agent-state` schedules a delayed check when the desktop app isn't running, a hidden `shogun hook notify-pending` command runs it, and `shogun app` selects a worktree.
- `desktopApp`: a notification watch fed by the board's agent detection, the two switches in Settings, the click handling and the request that opens a worktree.
- macOS packaging: a Swift helper app (`ShogunAi Notifications.app`) built by Gradle with `swiftc` into the app's resources, signed with the app's identity; release builds need Xcode's command line tools on the macOS runners, which they already have.
- Docs: an architecture note on notifications (both languages) and the roadmap.
