## Context

`WorktreeViewModel.refresh()` only re-ran `ListWorktreesUseCase`. `ListEligibleBranchesUseCase` filtered out any local branch already checked out (base repository or another worktree), so those branches simply didn't appear in the "Existing branch" dropdown, with no indication of why.

## Goals / Non-Goals

**Goals:**
- Manual reload also refreshes local branches, without adding a new button or changing the reload UX.
- Checked-out branches are visible but not selectable, with a reason shown inline.

**Non-Goals:**
- No change to worktree list reload behavior, focus-triggered reload, or create-from-branch Git command behavior.
- No change to `WorktreeViewModel.selectedBranch`'s type or the `createFromBranch(branch: String)` call sites.

## Decisions

- **Reuse and rename `ListEligibleBranchesUseCase` → `ListLocalBranchesUseCase`** instead of adding a second use case, since it was used in exactly one place (the "Existing branch" dropdown) and the new behavior is a strict superset of the old one (all branches instead of a filtered subset).
- **Introduce `BranchOption(name, isCheckedOut)`** instead of returning `List<String>` and computing checked-out status separately in the UI layer, so the flag is derived once in the use case (which already has the `git worktree list --porcelain` output) rather than duplicated in `WorktreeViewModel`/`WorktreeScreen`.
- **Keep `WorktreeViewModel.selectedBranch` as `String?`** (branch name) rather than `BranchOption?`, mapping `BranchOption ↔ String` in `WorktreeScreen.kt` via `localBranches.find { it.name == selectedBranch }`, to avoid touching `createFromBranch(branch: String)` and its existing tests.
- **Add `enabled: (T) -> Boolean = { true }` to the generic `DropdownSelector`** rather than forking a worktree-specific dropdown, since `ProjectConfigScreen` also uses `DropdownSelector` (for `TerminalEmulator`) and the default value preserves its behavior unchanged.
- **`refresh()` only calls `loadLocalBranches()` when `createMode == EXISTING_BRANCH`**, avoiding an unnecessary `for-each-ref`/`worktree list` call when the user is in "New branch" mode.

## Risks / Trade-offs

- [Renaming `ListEligibleBranchesUseCase` is a breaking change to that use case's public contract] → No external consumers outside this module; call sites and tests updated in the same change.
- [Disabled dropdown entries could be missed by screen readers without a visible reason] → The label itself embeds the reason ("(ya tiene un worktree)") rather than relying on a separate disabled-state affordance.
