# Proposal

## Why

Many errors reach the user as raw command output: "Command `git worktree add …` failed with code 128" followed by Git's stderr, or the same for `simctl` and `adb`. The user has to work out what failed and what to do next. And errors appear wherever the screen happened to have a banner, not where the failed action was started. This is the roadmap's "Understandable errors" (`docs/roadmap.md`). Inside the epic, the new layout gives each error a natural place: the dialog, the worktree detail, the project group or the view.

## What Changes

- Every error says in plain words what failed and what to do next, in the selected language.
- The technical output (command, exit code, the tool's own output, unchanged) stays available behind a collapsed "Technical details" with a copy action.
- Frequent Git failures are recognized from their output and explained specifically: another Git process holds a lock, permission denied, the folder isn't a repository, the branch already exists, the worktree has local changes.
- Errors are shown where the action happened: inside the dialog that started it, in the worktree's detail, in the project's group header, or at the top of the view for view-wide failures.
- Errors stay until dismissed or until the same action succeeds. Retryable failures (reloading, starting a device, stopping a daemon) offer "Try again".

## Capabilities

### New Capabilities

- `error-presentation`: how errors are explained (summary and next step), how technical details are kept available, how frequent Git failures are recognized, where each error is shown, and how it is dismissed or retried.

### Modified Capabilities

- `ui-localization`: the output of external tools is shown unchanged in the error's technical details instead of inside the message.

## Impact

- `ui/i18n/`: `worktreeError(...)` is replaced by `describe(error): UserFacingError` with a summary, a next step and optional technical details, for every `WorktreeError` and `CommandExecutionException`. Strings for every case in both languages.
- `core`: `classifyGitFailure(stderr)` recognizes the frequent Git causes. `GitCommandFailed` keeps its raw output.
- The `Notice` component's details slot (from `ui-design-foundations`) gets "Technical details" with copy and an optional "Try again".
- `ProjectWorktrees`, `WorktreeBoard`, `MachineResources` and the dialogs keep their errors scoped and know which action to retry.
- Docs: `docs/architecture/ui.md` (errors) in both languages.
