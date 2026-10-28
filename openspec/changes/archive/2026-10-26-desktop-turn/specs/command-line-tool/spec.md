# Spec Delta

## ADDED Requirements

### Requirement: Report the desktop turn in status
`shogun status` SHALL show on the line of the worktree that holds the desktop turn that it holds it and until when, and on the line of each waiting worktree its place in line. `shogun status --json` SHALL add `desktop` to every worktree object: `null`, `{ "role": "holder", "since", "expiresAt" }` or `{ "role": "waiting", "position", "since" }`, with ISO-8601 times.

#### Scenario: Holder and waiter
- **WHEN** `TASK-1` holds the desktop turn until 14:10 and `TASK-2` is first in line
- **THEN** `TASK-1`'s line says it holds the desktop until 14:10 and `TASK-2`'s line says it waits for it, first in line

#### Scenario: JSON
- **WHEN** the user runs `shogun status --json` while `TASK-1` holds the turn and no other worktree waits
- **THEN** `TASK-1`'s object has `desktop.role` `holder` with its `expiresAt`, and every other worktree object has `desktop: null`

#### Scenario: Turn held by another repository
- **WHEN** a worktree of `api` holds the turn and the user runs `shogun status` in `main-repo`
- **THEN** no line of `main-repo` mentions the desktop

## MODIFIED Requirements

### Requirement: Tell the agent how to check its desktop app
When the repository declares the desktop app provider on macOS, the context printed by `shogun hook session-start` SHALL say to check the app with `shogun headless start` and the Hot Reload MCP instead of computer use, to capture it with `shogun headless capture` instead of the MCP's screenshot, to re-read the tree after each view change, to hide it again with `shogun headless start` after `restart`, what still needs the desktop, and that for it the agent takes the desktop turn with `shogun desktop acquire --wait` first.

#### Scenario: Context with the provider
- **WHEN** a Claude Code session starts on macOS in a worktree of a repository that declares the provider
- **THEN** the context mentions `shogun headless start`, `shogun headless capture`, not to use the MCP's `take_screenshot`, hiding again after `restart`, what still needs the desktop and `shogun desktop acquire --wait` for it

#### Scenario: Context on another platform
- **WHEN** a session starts on Linux in a worktree of a repository that declares the provider
- **THEN** the context has no line about `shogun headless`
