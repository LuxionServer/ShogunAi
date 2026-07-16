## Context

`CreateWorktreeUseCase` currently only trims the task id and checks it's non-empty (`CreateWorktreeUseCase.kt:30-31`) before using it verbatim to build a Git branch name (`"${branchType.prefix}/$id"`) and a worktree directory path (`config.worktreePathFor(id)`). Git branch (ref) names reject spaces and several other characters, so a task id like `TASK 123` reaches `git worktree add ... -b feature/TASK 123`, which Git rejects, and the failure surfaces as a raw `WorktreeError.GitCommandFailed` with Git's own message instead of an actionable validation error. `WorktreeScreen`'s `OutlinedTextField` (`WorktreeScreen.kt:54-59`) does no normalization or validation either — it only requires non-blank text to enable the submit button.

## Goals / Non-Goals

**Goals:**
- Make task ids with spaces work by normalizing them (trim, spaces → `-`) instead of failing.
- Reject task ids that are still invalid as Git ref names after normalization, with a typed, actionable error.
- Give the task id field on `WorktreeScreen` the same normalization/validation so the user sees the sanitized value and an inline error before submitting, not just a failure after the fact.
- Define the rule as a small reusable capability (`input-validation`) so future free-text fields that become Git refs or path segments don't reinvent it.

**Non-Goals:**
- Validating every input field in the app (project name, agent command, secret file names, custom terminal command). None of those currently have a demonstrated bug; they're left as-is.
- Full compliance with Git's complete `check-ref-format` rule set (e.g. Unicode normalization, `@{`, control characters). We cover the practical cases relevant to a typed task id.
- Changing how branch names are composed (`<prefix>/<id>`) — only the `<id>` portion is normalized/validated.

## Decisions

- **Normalize before validate, and do both in `CreateWorktreeUseCase`, not only in the UI.** The use case is the single source of truth per the existing architecture (domain owns validation, returns typed `Result`/`WorktreeError`); the UI mirrors the same logic so the field can show live feedback, but the use case must not trust the UI to have sanitized input. Alternative considered: validate only in the ViewModel — rejected because any future caller of `CreateWorktreeUseCase` (tests, other screens) would bypass validation.
- **Normalization rule: `trim()` then replace runs of whitespace with a single `-`.** Matches the user's request literally (`" " → "-"`) and collapses multiple spaces into one hyphen rather than one hyphen per space, avoiding ids like `TASK---123`.
- **Validation rule: reject if, after normalization, the id is empty or contains any of Git's disallowed ref characters/sequences** (space — shouldn't remain after normalization but kept as a defense-in-check —, `~ ^ : ? * [ \`, the two-dot sequence `..`, a trailing `.lock`, or a leading/trailing `.` or `/`). Implemented as a small allow-list regex plus explicit checks, kept private to `CreateWorktreeUseCase` for now since it's the only consumer; promoted to a shared utility only if a second consumer appears (per the `input-validation` capability spec, not a new module yet).
- **New `WorktreeError.InvalidTaskId(rawInput: String)` variant** rather than reusing `GitCommandFailed`, so the screen can show a clear "invalid task id" message instead of Git's raw stderr.
- **`WorktreeViewModel`/`WorktreeScreen` normalize the field's value on change** (so the displayed text already shows `-` instead of spaces) and disable/flag submission when the normalized id would still be invalid, reusing the same normalize/validate functions the use case calls (extracted as top-level functions in the use case's file so both layers call the identical logic instead of duplicating the regex).

## Risks / Trade-offs

- [Collapsing multiple spaces into one hyphen changes the resulting branch name shape users might expect] → Documented in the proposal/spec scenarios so it's an explicit, intentional behavior, not a silent surprise.
- [Regex-based validation won't catch every edge case `git check-ref-format` rejects] → Acceptable: the goal is fixing the observed spaces bug and covering the common invalid-ref cases, not reimplementing Git's ref validator.
- [Duplicating normalize/validate logic between use case and ViewModel could drift] → Mitigated by sharing the same functions (see last Decision) rather than two implementations.

## Open Questions

None — scope is intentionally limited to the task id field.
