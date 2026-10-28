# Proposal

## Why

The control tower only updates in real time while its window has focus. `background-refresh-cost` (2026-10-19) paused the 3-second watcher whenever the window lost focus, assuming an unfocused window isn't being looked at. With several monitors that doesn't hold: the window sits visible on a second screen while the user works elsewhere, rows stay frozen, and everything catches up at once when focus returns. That design also meant to combine focus with the minimized state, and the code only looks at focus.

## What Changes

- The watcher counts the main window as active while it is shown, focused or not. It pauses only while the window is minimized, hidden in the tray, or the app is hidden (Cmd+H on macOS).
- That state comes from the window and the app (minimized state, window visibility, app hidden events), not from focus. A state the platform doesn't report counts as shown, as before.
- The light refresh on focus stays as it is.

## Capabilities

### New Capabilities

### Modified Capabilities
- `worktree-auto-refresh`: checks keep running while the window is visible but unfocused, and pause only while it is minimized or hidden.

## Impact

- `desktopApp`: `main.kt` (computes whether the main window is shown and tracks the app hidden state), `AppShell.kt` (feeds that state to `WorktreeBoard.setWindowActive` instead of focus).
- Tests: `WorktreeBoardTest` (the inactive window case, now minimized or hidden).
- Docs: `docs/architecture/ui.md` and `ui.es.md`.
