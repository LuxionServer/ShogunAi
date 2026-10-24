# Proposal

## Why

Agents can only check a Compose Desktop app today by driving the real desktop with computer use: the app comes to the front, takes the mouse and keyboard away from the person, and two agents can't do it at once. Mobile and web already avoid the desktop (one emulator or simulator per worktree, headless browsers); desktop apps, ShogunAi itself first, are the gap. A spike on macOS (2026-10-25) showed the pieces exist: Compose Hot Reload's MCP server drives an app through its semantics in the background and finds the app of its own worktree, an app launched as a UI element never comes to the front, a hidden app keeps working, and its window can be captured by window id even when it is covered or hidden.

## What Changes

- A new opt-in `desktopApp` provider in `.shogunai.json` declares a repository's Compose Desktop app by its Gradle module, so `shogun` can launch it generically, not only for ShogunAi.
- New `shogun headless start|stop|status|capture` commands act on the app of the current worktree:
  - `start` launches the app through Compose Hot Reload's run task, detached, as a UI element (no Dock icon, no menu bar, never activated), with an isolated user home per worktree, waits for its window and hides it, so the person never sees it nor loses focus. Several worktrees run their apps at once.
  - `capture` writes a PNG of the app's window, captured by its window id, which shows the app's current content even while it is hidden or covered. It replaces the Hot Reload MCP's `take_screenshot`, which copies the screen region instead of the window and can leak whatever is on top.
  - `stop` ends the app; `status` reports it and the permissions it needs.
- Driving the app (semantic tree, click, type, scroll, reload) is left to the Hot Reload MCP server (`hotMcpServer`) that the agent runs in its worktree; `shogun` doesn't wrap it.
- Removing a worktree (`shogun remove`, `shogun clean --yes`, the desktop app's removal) stops its headless app.
- The `SessionStart` context of a repository with the provider tells the agent to use `shogun headless` and the Hot Reload MCP instead of computer use, not to use the MCP's screenshot, and what still needs the desktop.
- macOS only in this change. On Windows and Linux the commands fail with a clear "not supported on this platform" error.
- What the background path can't reach stays for the planned desktop turn: real keyboard input (AWT drops key events for windows that aren't active), shortcuts, native menus, the menu bar item, system dialogs, hover and hit-testing.

Prerequisite, as a separate change merged before this one's implementation: upgrade ShogunAi to Compose Multiplatform 1.12, which the Hot Reload MCP server needs.

## Capabilities

### New Capabilities
- `headless-desktop-app`: the `desktopApp` provider and running a worktree's Compose Desktop app in the background on macOS: launch without activation, isolated home, hiding, window capture, stop, status, permissions, and stopping it with its worktree.

### Modified Capabilities
- `command-line-tool`: the `shogun headless` commands, stopping a worktree's headless app when the worktree is removed or cleaned up, and the `SessionStart` context for repositories with the provider.

## Impact

- `core`: the provider's declaration and validation in `.shogunai.json`, a headless app use case behind new ports (app launcher, window service for macOS), a Gradle init script that sets the system properties on the app's run task, state in the worktree's Git administrative folder.
- `cli`: the `headless` command group, help, JSON output, the session-start context, cleanup and removal.
- macOS tools used as argv: `screencapture`, `osascript` (JavaScript for Automation, to list windows and hide the app); permissions: Screen Recording for capture, possibly Accessibility for hiding.
- ShogunAi's own repository declares the provider and the Hot Reload MCP server for its agents.
- Docs: a page on checking a desktop app in the background (bilingual), the roadmap entry "Checking a running app without taking over the desktop" moves out of exploration, and the desktop turn lists what remains for it.
