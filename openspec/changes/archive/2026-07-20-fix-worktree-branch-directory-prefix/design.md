## Context

`CreateWorktreeUseCase` (new-branch flow) derives the worktree directory as `worktreePathFor(taskId)`, i.e. the plain task id with no `BranchType` prefix (`feature`/`fix`), while it names the created branch `"${branchType.prefix}/$taskId"`. `CreateWorktreeFromBranchUseCase` (existing-branch flow) derives the directory via `ProjectConfig.worktreePathForBranch(branch)`, which — before this change — only replaced `/` with `-` in the full branch name. As a result, attaching `feature/TASK-123` (a branch the app itself would have created) produced `feature-TASK-123` instead of `TASK-123`, an asymmetry visible to the user as an unwanted prefix.

This asymmetry was a known, deliberate trade-off from the original design (see `openspec/changes/archive/2026-07-16-create-worktree-from-existing-branch/design.md`), which rejected stripping the last path segment because two branches with the same suffix but different prefixes (`feature/x`, `fix/x`) would then collide on one directory. That reasoning is still valid for *arbitrary* branches, but it doesn't need to apply to branches that follow the app's own known `BranchType` prefixes, since only one `BranchType` variant of a given id would ever be created by this app in the first place.

## Goals / Non-Goals

**Goals:**
- Make the existing-branch flow's directory derivation consistent with the new-branch flow for branches using a known `BranchType` prefix (`feature/`, `fix/`), so re-attaching such a branch reproduces the same directory the app would have created for it.
- Preserve the collision-avoidance property of the original `/` → `-` sanitization for any branch that does not start with a known `BranchType` prefix (e.g. `develop`, `hotfix/security-patch`).

**Non-Goals:**
- Changing how the new-branch flow names branches or directories.
- Handling collisions between a known-prefixed branch and an unrelated branch that happens to sanitize to the same directory name (e.g. `feature/x` and a literal branch named `x`) — this is the same pre-existing, accepted risk class documented in the original design's Risks section, now narrowed rather than widened.

## Decisions

### Strip only known `BranchType` prefixes, fall back to full sanitization otherwise

`worktreePathForBranch` first checks whether the branch starts with `"${it.prefix}/"` for any `BranchType` entry. If so, that prefix is removed before sanitizing; otherwise the full branch name is sanitized as before (`/` → `-`).

**Alternative considered**: always strip the branch's first path segment regardless of whether it matches a known `BranchType`. Rejected — this reintroduces the exact collision risk (`feature/x` vs `fix/x`) the original design explicitly avoided, for no benefit, since foreign prefixes were never produced by this app's new-branch flow and don't need round-trip consistency with it.

**Alternative considered**: keep the directory including the prefix, and instead change the new-branch flow to include the prefix in the directory too. Rejected — this changes existing, already-created worktrees' expected directory convention and was not what the user wanted (they consider `feature-TASK-123` an unwanted prefix, not an unwanted omission of it on the other flow).

## Risks / Trade-offs

- [A branch literally named `TASK-123` and a branch named `feature/TASK-123` now sanitize to the same directory (`TASK-123`), where before they sanitized to different directories (`TASK-123` vs `feature-TASK-123`)] → Mitigation: this is an ordinary `WorktreeAlreadyExists` if both are attached, no different in kind from the pre-existing collision risk already accepted in the original design; considered rare enough not to need special handling.
