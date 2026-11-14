# Agent notifications

ShogunAi shows a system notification when an agent stops working because it needs the user, or because it is done, and nothing else: no notification per event, no sound of its own, no floating window. The worktree's row and the menu bar menu stay the main signal; the notification is for when the user is looking elsewhere.

## When a notification is shown

The rules live in `core`, in the pure `AgentNotificationTracker.advance`, which both the desktop app and the command line call with what they observed:

- Only a change from working to blocked or done counts. An agent seen for the first time, a repeated state and any other change (blocked to done, idle to done) never notify.
- The change waits 8 seconds and is confirmed by reading the agent again. An agent that is working again by then, as between chained turns and subagents, or that is gone, is dropped.
- A confirmed agent is silent when its Herdr pane is focused, when its switch in Settings is off (Blocked is on by default, Done is off), or when its runner notifies that change by itself.
- Everything confirmed together makes one notification that counts the agents and lists them, blocked ones first; clicking it opens the first one's worktree.

### Runners that already notify

To avoid doubling a notification, ShogunAi reads the runners' own settings, read-only and cached for a minute:

- Herdr notifies on the desktop when `[ui.toast] delivery` is `terminal` or `system` in its `config.toml`; ShogunAi then leaves the agents in Herdr panes to Herdr.
- Orca keeps its settings in its private SQLite database (`profiles/<profile>/profile-state.db`). Its `notifications` decide whether it notifies agents that need input (`agentNeedsInput`, which follows `agentTaskComplete` when unset) and agents that finish (`agentTaskComplete`). When the database can't be read, ShogunAi assumes Orca's defaults and stays silent for Orca's agents.

## Who observes the agents

- **Desktop app.** `AgentNotificationWatch` is fed by `WorktreeBoard` after every agent detection, and asks the board for one more detection when a change's grace period ends. While the window is hidden and a switch is on, the board keeps detecting agents at the refresh cadence's unfocused interval, without reading Git or devices.
- **Command line, while the app is closed.** `shogun hook agent-state` sees Claude's transition between the session's previous record and the new one. It adds the session to `~/.shogunai/notifications/pending.json` and starts `shogun hook notify-pending` in the background with the same JVM, so Claude Code isn't delayed. That process waits for the grace period, rebuilds each session's state from the session store and Herdr, and notifies once for everything due. If the app has started meanwhile, it drops what is pending.

## How each system shows it

`SystemNotifier` is the port, and `notificationPorts` picks the adapter for the host:

| System | Mechanism | Click |
|---|---|---|
| macOS | `ShogunAi Notifications.app`, a small Swift helper bundled in the app's resources, posts through `UNUserNotificationCenter` under ShogunAi's name. | macOS relaunches the helper, which writes an `open-worktree` request into `~/.shogunai/app-requests` and opens ShogunAi. |
| Linux | `notify-send --action … --wait`. Without `notify-send`, nothing is shown. | The app reads the action from `notify-send`'s output. The command line posts without an action. |
| Windows | A notification-area icon (`java.awt.TrayIcon`). | The icon's action listener. The command line doesn't notify on Windows. |

Each notification carries a badge drawn by ShogunAi, `RenderedNotificationImages`: amber with "!" when the agent needs the user, green with a check mark when it is done, with the agent's initial or, for several agents, how many. It is drawn once into `~/.shogunai/notifications/images` and attached to the macOS notification (macOS keeps the app's icon on the left and shows the badge as a thumbnail) or passed as `notify-send`'s icon. Vendors' logos aren't used. Where it can't be drawn, the notification goes without it.

The first notification on macOS asks for permission; the choice is kept in System Settings, under Notifications, as "ShogunAi". The JVM's own tray notifications aren't used on macOS: they go through the deprecated `NSUserNotificationCenter`, aren't shown on current macOS and don't report clicks.

The helper is built by the `buildNotifier` Gradle task with `swiftc` on macOS (skipped elsewhere), signed with the app's Developer ID identity when one is given and ad hoc otherwise, and placed in Compose's app resources, so `./gradlew :desktopApp:run` uses it too.

## Opening the worktree

A click goes through the same request the command line uses: `AppRequest` with the action `open-worktree`, a project and a worktree path. `shogun app <path>` sends it too when the path is inside a linked worktree. The app shows its window and reveals the worktree in "All worktrees", or shows the project when the worktree no longer exists. Older apps ignore the action.
