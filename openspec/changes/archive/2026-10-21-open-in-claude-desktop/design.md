# Design

## Context

Worktree open actions are `OpenKind` values (`TERMINAL`, `EDITOR`, `FOLDER`) listed by `ProjectWorktrees.openActions` and dispatched by `open`; detail, row menu, palette and tray all consume that list. Launchers are ports in `domain/io/ToolLaunchers.kt` (`EditorLauncher`, `FolderLauncher`) implemented in `infrastructure/EditorLaunchers.kt` and run commands as argv through `ShellCommandExecutor.runTool`. `LocalToolDetector` reports installed terminals and editors from `ToolCatalog` (`MacApp` bundle ids and names) into `DetectedTools`. See proposal.md for motivation.

Claude Desktop (bundle id `com.anthropic.claudefordesktop`, `Claude.app`) registers the `claude` URL scheme. Its link `claude://code/new?folder=<url-encoded path>&source=shogunai` is the one its Finder action "New Claude Code Session Here" uses. Verified on macOS on 2026-10-21: it opens a local Claude Code session with that folder as cwd (Claude Desktop's `main.log` records `LocalSessions.saveTrust: cwd=<folder>`).

## Goals / Non-Goals

**Goals:**
- One-click start of a Claude Code session in Claude Desktop for a worktree, on macOS.
- Fit the existing open-action machinery: same places, same error reporting, availability by detection.

**Non-Goals:**
- Focusing or resuming an existing session: the link only creates new ones.
- An agent selector or launch command: Claude Desktop always runs Claude Code.
- Windows support: whether the link accepts `folder` there is unverified, so it is a follow-up.
- Linux: Claude Desktop doesn't exist there.

## Decisions

**Use the deep link, not just opening the app.** `open -b <bundle> <folder>` would only launch the app. The link carries the folder and opens a session. The command is the argv `["open", "claude://code/new?folder=<encoded>&source=shogunai"]`, with the path encoded as a URL component (spaces as `%20`, not `+`).

**New port `ClaudeDesktopLauncher` next to `FolderLauncher`, not an `EditorId`.** It isn't an editor: editors open a path in an IDE and are chosen as a default per project, while this action has no choice and always applies. The macOS implementation lives beside `SystemFolderLauncher`; it takes the `HostOs` and fails with `ClaudeDesktopLaunchFailed` on other systems, though the UI never offers it there.

**Detection through `ToolCatalog` and `DetectedTools.claudeDesktop`.** `ToolCatalog` gets `claudeDesktopApp = MacApp(listOf("com.anthropic.claudefordesktop"), listOf("Claude.app"))`, checked with the existing `isMacAppInstalled` (bundle id through `mdfind`, then the application folders). `DetectedTools` gets a boolean, true only on macOS. The action is offered when it is true.

**`OpenKind.CLAUDE_DESKTOP`, listed after the editor and before the folder.** `openActions` adds it when detected and the worktree isn't prunable; `open` dispatches it. It doesn't call `onUsed`, since it records the editor/terminal in use for the worktree. Tray and palette get it through the shared list, so their models only need to accept the new kind.

**Typed error.** `WorktreeError.ClaudeDesktopLaunchFailed(detail)` with English and Spanish texts, like `EditorLaunchFailed`, so the failure follows the same display path.

## Risks / Trade-offs

- [The link is undocumented and may change or be removed in a Claude Desktop update] → the launch only fails visibly (or opens the app without a session); keep the URL in one constant, covered by a test, so a fix is a one-line change. Re-verify on new Claude Desktop versions.
- [`open` succeeds even if Claude Desktop ignores the link] → we can't detect that; the user sees no session. Accepted, same as other fire-and-forget launchers.
- [A new session per click can pile up sessions] → inherent to the link; documented in the spec.
- [Installed-but-outdated Claude Desktop without the `code/new` route] → same as the first risk; no version check is attempted.
