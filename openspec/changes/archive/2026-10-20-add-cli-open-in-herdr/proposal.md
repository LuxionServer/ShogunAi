## Why

Opening a worktree in Herdr, with its environment and the project's agent, is only possible from the desktop app. An agent working from a terminal, or a user who has just run `shogun new`, has to call Herdr's CLI by hand and repeat what the app does: group the worktree under its repository, give the shell the worktree's environment and start the agent.

## What Changes

- Add `shogun open <task id | path | branch> --herdr [--agent <name>] [--json]`, which opens the worktree in Herdr exactly as the desktop app's "Open in Herdr" does, or focuses its workspace when Herdr already has it open.
- `--herdr` is required for now, so other destinations (terminal, IDE) can be added later as more flags without changing what `open` means.
- `--agent` picks one of the project's agents by name; without it the project's default agent runs.
- Unlike `remove`, the target may be the main worktree.

## Capabilities

### New Capabilities

### Modified Capabilities

- `command-line-tool`: a new `open` command, added to the commands that accept `--json`.
- `herdr-integration`: opening a worktree in Herdr is also available from the command line, with the same workspace reuse and failure reporting.

## Impact

- `core`: `resolveWorktreeTarget` gets a variant that accepts the main worktree; `OpenWorktreeInHerdrUseCase` is reused as is.
- `cli`: `CliCommand.Open`, its parser, help, JSON result and `ShogunCli.open`, with tests.
- Docs (English and Spanish): the CLI guide and the Herdr integration page; the roadmap if it mentions this follow-up.
- `desktopApp`: no change.
