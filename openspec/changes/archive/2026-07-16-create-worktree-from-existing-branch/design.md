## Context

`CreateWorktreeUseCase` only supports `git worktree add <path> -b <branch>`, i.e. always creating a new branch. It performs upfront validations (base repo exists, destination free, secrets present), runs the `git worktree add` command, copies secret files into the new worktree, and rolls back (`git worktree remove --force`, `git branch -D`) if either the Git LFS post-checkout hook or the secret copy fails. `ListWorktreesUseCase` already knows how to parse `git worktree list --porcelain` into `Worktree` values, including which branch (if any) each worktree has checked out.

There is no existing way to list local branches, or to attach an existing branch to a new worktree without creating one via the CLI by hand.

## Goals / Non-Goals

**Goals:**
- Let the user create a worktree for a branch that already exists locally, reusing the same secret-copy and rollback semantics as the existing flow.
- Let the user see which local branches are actually eligible (i.e. not already checked out somewhere) before picking one.
- Surface Git's "one worktree per branch" constraint as a typed, actionable error instead of a raw `GitCommandFailed`.

**Non-Goals:**
- Fetching or listing remote-only branches (not yet present locally). The user is expected to have already fetched/created the branch locally.
- Automatically moving a branch out of the base repository's own checkout so it can be reused in a new worktree. If the requested branch is checked out in the base repository itself, this is surfaced as `BranchAlreadyCheckedOut`, same as if it were checked out in any other worktree; the user resolves it manually (e.g. switching the base repo to another branch first).
- Renaming, rebasing, or otherwise mutating the branch — this feature only wires an existing branch to a new worktree directory.

## Decisions

### Extract the shared "add + copy secrets + rollback" flow

`CreateWorktreeUseCase` and the new `CreateWorktreeFromBranchUseCase` differ only in (a) the `git worktree add` argv (`-b <branch>` vs. plain `<branch>`) and (b) the upfront validation specific to each (new branch has no existence check; existing branch must exist and must resolve to a real ref). Everything after that — running the command, detecting the Git LFS failure signature, copying secrets, rolling back on failure — is identical.

Rather than duplicating that logic, extract it into an internal function (e.g. `addWorktreeAndCopySecrets`) in the `usecase` package, parameterized by the full `git worktree add` argv and the branch name to roll back (`git branch -D`) if LFS is missing. Both use cases call it after their own upfront checks.

**Alternative considered**: give `CreateWorktreeUseCase` an optional "existing branch" parameter instead of a second use case. Rejected because the two flows have different, non-overlapping upfront validations (task id + branch type vs. a plain branch name that must already exist) and different domain errors (`WorktreeAlreadyExists` path derivation, `BranchNotFound`) — folding both into one signature would make the use case harder to read for either caller. Two small use cases sharing one internal helper keeps each call site simple.

### Deriving the worktree path from the branch name

`ProjectConfig.worktreePathFor(taskId)` already joins `worktreesRoot` with a single path segment. Existing branches don't necessarily follow the `<prefix>/<task-id>` convention (e.g. `develop`, `hotfix/security-patch`), so the new flow needs a directory name for arbitrary branch names. Decision: replace every `/` in the branch name with `-` and reuse `worktreePathFor` with that sanitized string (e.g. `hotfix/security-patch` → `<worktreesRoot>/hotfix-security-patch`). This guarantees a valid single-segment directory name and keeps branches like `feature/x` and `fix/x` from colliding into the same directory.

**Alternative considered**: use only the branch's last path segment (mirroring how task ids map to `feature/<id>`). Rejected because two branches with the same suffix but different prefixes (`feature/x` and `fix/x`) would collide on the same worktree directory.

### Detecting "branch already checked out elsewhere"

Git's `worktree add <path> <branch>` fails with a message containing `is already checked out at '<path>'` when the branch is in use by another worktree (including the base repository). Decision: match on that substring in `stderr`, the same pattern already used for the Git LFS markers, and map it to `WorktreeError.BranchAlreadyCheckedOut`, extracting the conflicting path from the message with a small regex when possible (`null` if the message shape doesn't match, so the UI still gets a typed error either way).

### Listing eligible branches

Decision: add `ListEligibleBranchesUseCase` that (1) runs `git for-each-ref --format=%(refname:short) refs/heads` to get all local branches, and (2) runs `git worktree list --porcelain`, reusing the existing internal `parseWorktreeList` to know which branches are already checked out somewhere (including the base repo). The result is all local branches minus the checked-out ones.

**Alternative considered**: instantiate `ListWorktreesUseCase` from inside `ListEligibleBranchesUseCase`. Rejected in favor of calling `parseWorktreeList` directly with a fresh `git worktree list --porcelain` invocation — composing use cases from other use cases isn't a pattern used elsewhere in this codebase, while reusing the internal parsing function keeps the same abstraction level as `ListWorktreesUseCase` itself.

### UI: mode toggle in the create dialog

Decision: extend the existing "create worktree" dialog with a two-option toggle ("New branch" / "Existing branch") instead of a separate screen or dialog. Selecting "Existing branch" swaps the task id + branch type inputs for a branch picker backed by `ListEligibleBranchesUseCase`, loaded once when that mode is first selected. This keeps worktree creation as a single entry point in the UI.

## Risks / Trade-offs

- [The eligible-branches list can go stale between load and submit if a branch gets checked out elsewhere in the meantime (e.g. via another terminal)] → Mitigation: this is already handled at the Git layer — `git worktree add` will fail with the same "already checked out" error, which is mapped to `BranchAlreadyCheckedOut` regardless of when the conflict happened.
- [Sanitizing `/` to `-` in branch names could theoretically collide with a branch that already contains literal hyphens matching another branch's sanitized name (e.g. `feature/a-b` and `feature-a/b` both sanitize to `feature-a-b`)] → Mitigation: same class of collision already exists implicitly for any path-based naming scheme; treated as acceptably rare and surfaced as an ordinary `WorktreeAlreadyExists` if it happens, no special handling needed.

