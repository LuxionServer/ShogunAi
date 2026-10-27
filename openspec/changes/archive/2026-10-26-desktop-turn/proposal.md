# Proposal

## Why

Some checks can only be done by an agent driving the real desktop with computer use: real keyboard input and shortcuts, native menus, the menu bar, system dialogs, hover and hit-testing, and apps of other toolkits, which `shogun headless` doesn't reach. The desktop (screen, mouse, keyboard and the focused app) is one resource for the whole machine, so two agents using it at once step on each other and on the person working there. Emulators and simulators already solved the same problem with leases and a `--wait` queue; the desktop needs the same discipline, plus a turn that can't be kept by an agent that stopped or crashed. And the background apps `shogun headless` starts are invisible in the control tower today, so a failure to stop one when a worktree is removed from the app goes unnoticed.

## What Changes

- New `shogun desktop acquire [--wait] [--wait-timeout <minutes>]`, `shogun desktop release` and `shogun desktop status`: one turn for the whole machine, held by one worktree at a time. Acquiring while another worktree holds it fails with who holds it and until when, or waits in a first-in, first-out queue shared with the device pools' waiting model. Acquiring again by the holder renews the turn.
- The turn expires on its own after the desktop turn duration (a machine setting, 10 minutes by default, editable in Preferences > Resources) unless the holder renews it. An agent's working state does not renew it, so a stale record can't keep the desktop.
- The turn is released when the holder's Claude Code session ends its response (`Stop`) or ends (`SessionEnd`), through the existing `shogun hook agent-state`, and when the holder's worktree is removed, which also drops its waits.
- `shogun status` shows, per worktree, whether it holds the desktop (until when) or waits for it (its place); `shogun status --json` adds a `desktop` field to each worktree.
- The control tower shows the turn: the worktree row's execution cell says when a worktree holds or waits for the desktop, the detail shows it with a "Release" action for the person, and the sidebar's Machine section gets a "Desktop" entry listing the holder and the queue while either exists.
- The headless apps of `shogun headless` appear in the worktree views: the row's execution cell shows a running background app, and the detail shows its state with Stop and the log's path. Reading this state for the views only checks the recorded process, without macOS window tools.
- Removing or cleaning up a worktree from the desktop app shows a notice when its headless app couldn't be stopped, as the command line already reports.
- The workflow guide and the session context tell agents to take the turn with `shogun desktop acquire --wait` before computer use and release it when done.

## Capabilities

### New Capabilities

- `desktop-turn`: the machine-wide turn for driving the desktop: acquiring, renewing, waiting, releasing and expiring it, its duration setting, releasing it from Claude Code's hooks and on removal, and `shogun desktop` on the command line.

### Modified Capabilities

- `command-line-tool`: `shogun status` reports each worktree's desktop turn; the session context for the desktop app provider points to the desktop turn for what still needs the desktop.
- `agent-workflow-guide`: the `resources` topic includes the rules for the desktop turn.
- `worktree-row`: the execution cell also shows the desktop turn and a running headless app.
- `worktree-detail`: the detail shows the desktop turn with "Release", and the headless app with Stop.
- `machine-resources`: a "Desktop" entry in the Machine section with the holder and the queue.
- `headless-desktop-app`: the desktop app reports a failure to stop the headless app on removal and cleanup.

## Impact

- `core`: a desktop turn store and use cases (acquire, renew, release, status, release by worktree), the waiting loop extracted from `AcquireWorktreeResourceUseCase` so devices and the desktop share it, the wait queue keyed by a queue name instead of only `ResourceKind`, a new `desktopTurnMinutes` in `ResourceSettings`, the agent-state hook and worktree removal releasing the turn, and a cheap headless app state read for the views.
- `cli`: the `shogun desktop` command group, `shogun status` text and JSON (additive field), guide and session-start text.
- `desktopApp`: execution cell, worktree detail, Machine section entry and view, Preferences > Resources field, removal and cleanup notices, English and Spanish strings.
- Machine state: a new `~/.shogunai/desktop-turn.json`; the existing `device-queue.json` gains a `desktop` queue. No change to `.shogunai.json`.
- Docs: architecture pages on resources and the CLI reference (English and Spanish), and the roadmap on archive.
