# Proposal

## Why

A user who works on a worktree with Claude Code in the terminal may prefer the Claude Desktop app for that session, but has to open it, start a Code session and find the worktree's folder by hand. Claude Desktop registers the `claude://code/new?folder=...` link (the one its own Finder action "New Claude Code Session Here" uses), so ShogunAi can start that session in one click, next to the terminal, IDE and folder actions.

## What Changes

- A new "Open in Claude Desktop" action opens a new Claude Code session in Claude Desktop with the worktree's folder as its working directory.
- The action appears in the worktree's detail, its row's context menu, the command palette and the tray submenu, next to "Open in <terminal>", "Open in <IDE>" and "Open folder".
- The action is offered only on macOS and only when Claude Desktop is installed; it is not offered for a worktree whose folder is gone (prunable).
- Every activation starts a new session: Claude Desktop can't focus an existing one through this link.
- A launch failure shows an error naming Claude Desktop with the command's error output.
- The action has no agent selector: it always starts Claude Code.

## Capabilities

### New Capabilities

- `worktree-claude-desktop-launch`: opening a worktree as a new Claude Code session in Claude Desktop, its availability and its failure reporting.

### Modified Capabilities

None.

## Impact

- Core: `ToolCatalog` (Claude Desktop bundle), `LocalToolDetector` and `DetectedTools` (installed or not), a new launcher port in `ToolLaunchers.kt` with a macOS implementation next to `SystemFolderLauncher`, and a `WorktreeError.ClaudeDesktopLaunchFailed`.
- Desktop: `AppContainer` (wiring and the `ToolUseCases` bundle), `OpenKind` and `ProjectWorktrees.openActions`/`open`, English and Spanish strings and error texts.
- Docs: `docs/architecture` page that lists the open actions, with its `.es.md` translation.
- Relies on an undocumented link of Claude Desktop that may change (see design.md).
