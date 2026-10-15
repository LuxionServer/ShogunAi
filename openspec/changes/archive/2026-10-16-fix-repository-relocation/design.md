## Context

`secretFiles` were already stored relative to the base repository when chosen with the file picker, but an entry typed by hand as an absolute path was stored as-is. `worktreesRoot` is an independent field. Git links the base repository and its worktrees with absolute paths (`<worktree>/.git` and `.git/worktrees/<name>/gitdir`).

## Decisions

- **Normalize secrets on load, not when the path changes.** When the form opens, the stored base path is still the right one to interpret absolute entries, so converting them to relative paths at that point is enough for them to follow any new path, even if it's typed character by character. Absolute paths outside the repository are left untouched.
- **`worktreesRoot` follows the base path only if it was tied to it.** The initial relative offset is computed (`.worktrees`, `..`, `../worktrees`); if it contains more than one `..` it's considered an independent location (another disk, another folder) and isn't touched. It's only updated while the current value matches the one derived from the previous base path, so a manual edit by the user is never overwritten.
- **Repair with `git worktree repair` after saving.** It only runs when the base path or `worktreesRoot` of an existing project changes. `git worktree list` still returns the old worktree paths; those that no longer exist but do exist under the new `worktreesRoot` are passed as arguments so Git also fixes the repository → worktree link.
- **`ViewModel` key by configuration.** The use cases are built with the `ProjectConfig`; including its `hashCode()` in the key avoids reusing a `ViewModel` with old paths.

## Risks / Trade-offs

- If the user moves the worktrees to a location that can't be derived from the previous `worktreesRoot`, `git worktree repair` without paths can't re-link them; Git's error is shown, or the worktrees show up as before (`prunable`).
