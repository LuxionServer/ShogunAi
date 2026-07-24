## Context

`WorktreeScreen`/`WorktreeViewModel` already has one loading-state precedent: `refresh()` and `loadLocalBranches()` each expose a boolean (`isLoading`, `isLoadingBranches`) that the Composable reads to show a `CircularProgressIndicator` and disable the "Reload" action. The create (new branch / existing branch) and remove actions call suspend use cases that run real `git` subprocesses via `ShellCommandExecutor`, but have no equivalent flag — their buttons stay enabled and clickable for the full duration of the call, which can be a perceptible/frozen-looking delay and allows duplicate submissions from repeated clicks.

## Goals / Non-Goals

**Goals:**
- Give the user visible, immediate feedback (spinner + disabled controls) for the three git-shell-backed actions currently missing it: create-new-branch, create-from-existing-branch, remove (including its force-retry state).
- Reuse the exact pattern already established by `refresh()`/`loadLocalBranches()` so the screen stays internally consistent, rather than inventing a second loading convention.
- Prevent duplicate submissions (e.g. double-clicking "Crear worktree") by disabling the triggering control while its use case is in flight.

**Non-Goals:**
- No changes to `OpenWorktreeTerminalUseCase` ("Terminal" button) — it's a fire-and-forget process launch outside this proposal's scope.
- No changes to `ProjectListScreen`/`ProjectConfigScreen` — their actions are synchronous local file I/O with no perceptible delay.
- No generic/reusable "loading button" component or design-system abstraction — three call sites following one existing pattern doesn't warrant a new abstraction.
- No changes to use case signatures, `ShellCommandExecutor`, or error handling — this is UI-state only.

## Decisions

- **One boolean flag per action family, not a single shared "isBusy" flag.** `isCreating` covers both create modes (they're mutually exclusive in the UI — only one form is visible at a time) and `isRemoving` covers the remove dialog (including its force-retry state). Two flags, not three, and not one: sharing `isCreating` across both create modes avoids redundant state without conflating unrelated actions (create vs. remove can theoretically be triggered from different, independent UI regions).
- **Disable the confirming control only, not the whole screen.** Matches the existing `refresh()` precedent (only the "Reload" button disables, the rest of the screen stays interactive). For remove, the dialog's confirm *and* cancel buttons both disable — cancelling mid-`git worktree remove` isn't meaningful once the process has started, and disabling both avoids state confusion if the user clicks cancel right as the result returns.
- **State lives in `WorktreeViewModel`, set/cleared with try/finally around the use case call** (same shape as `refresh()`), so the flag always clears even if the use case throws or returns a failed `Result`.

## Risks / Trade-offs

- [Very fast local operations (e.g. removing a worktree on an SSD) may show a spinner for only a few milliseconds, causing a visible "flash"] → Accepted: this already happens with the existing `refresh()` spinner and hasn't been a reported problem; not worth adding debounce/minimum-duration logic for a one-off UI polish change.
- [Two independent flags instead of a fully generic solution means a fourth future long-running action would need its own flag added by hand] → Accepted: matches the codebase's existing per-action-flag convention; revisit only if a fourth or fifth action makes the duplication actually painful.
