## Why

The worktree management screen already shows a loading indicator and disables its "Reload" action while the worktree/branch lists refresh, but the create-worktree (new branch and existing branch) and remove-worktree actions run real Git subprocess work with no visual feedback at all: their buttons stay clickable and the UI gives no sign that anything is happening. On a slower disk or repo, this reads as a frozen or unresponsive app and invites duplicate clicks (e.g. double-submitting a worktree creation).

## What Changes

- Add an in-progress loading state to the "Crear worktree" action for both create modes (new branch via `CreateWorktreeUseCase`, existing branch via `CreateWorktreeFromBranchUseCase`): show a spinner on/near the button and disable the confirm button (and the mode toggle) while the use case runs.
- Add an in-progress loading state to the worktree removal confirmation dialog's confirm button (including the force-retry state), disabling both the confirm and cancel actions and showing a spinner while `RemoveWorktreeUseCase` runs.
- Reuse the existing loading-state convention already established by `WorktreeViewModel.refresh()` / `loadLocalBranches()` (a boolean flag exposed to the Composable, rendered as a `CircularProgressIndicator` plus a disabled button) rather than introducing a new pattern.
- No changes to `OpenWorktreeTerminalUseCase` ("Terminal" button): it's a fire-and-forget process launch, not in scope.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `worktree-workspace`: the "Create a worktree for a task", "Create a worktree from an existing branch", and "Remove a worktree" requirements gain in-progress UI behavior (spinner + disabled controls) while their respective use cases run, matching the existing reload requirement's precedent.

## Impact

- `desktopApp` (or equivalent module) `ui/worktree/WorktreeViewModel.kt`: add `isCreating` / `isRemoving` state, set/cleared around the `create`, `createFromBranch`, `remove` calls.
- `ui/worktree/WorktreeScreen.kt`: render spinners and disable the create button, mode toggle, and dialog confirm/cancel buttons based on the new state.
- No changes to domain use cases, `ShellCommandExecutor`, or other screens (`ProjectListScreen`, `ProjectConfigScreen`) — their actions are local/synchronous and out of scope.
