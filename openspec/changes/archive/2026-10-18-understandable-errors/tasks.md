# Tasks

## 1. Core

- [x] 1.1 Add `GitFailureCause` and `classifyGitFailure(stderr)` next to the existing stderr checks; verify with `GitFailureCauseTest` using real Git messages for each cause and an unknown message.
- [x] 1.2 Run every Git command with `LC_ALL=C` (Git inherits the user's locale today); verify with a test of the Git command environment, and that the existing `--force` and Git LFS stderr checks still pass.

## 2. Descriptions

- [x] 2.1 Add `UserFacingError`, `TechnicalDetails`, `ErrorFix` and `Strings.describe(...)` covering every `WorktreeError`, every `GitFailureCause` and `CommandExecutionException`, following the table in `design.md`, with the strings in both languages; verify with a `StringsTest` case that every error type has a non-empty summary and next step in both languages, and that no summary contains the raw output.
- [x] 2.2 Remove `worktreeError(...)` and migrate every caller; verify the build passes and `grep` finds no caller.

## 3. Rendering and lifetime

- [x] 3.1 Extend `Notice` with the next step, fix button, "Try again" and the "Technical details" disclosure with copy; verify in the running app with a forced Git failure that the details show Git's output unchanged and copy as plain text.
- [x] 3.2 Store errors as `Throwable` plus retry in the create dialog, the worktree detail, the project groups, the machine views and the view header, and clear them only on dismiss or on success of the same action; verify with `ProjectWorktreesTest`, `WorktreeBoardTest` and `MachineResourcesTest` cases for retry, success clearing, and unrelated success keeping the error.
- [x] 3.3 Wire the fixes (`OpenProjectSettings`, `OpenPreferences`) to the shell's navigation; verify in the running app with a project whose repository was moved.

## 4. Docs

- [x] 4.1 Add an "Errors" section to `docs/architecture/ui.md` and `ui.es.md` (description, details, placement, lifetime); verify `mkdocs build --strict` succeeds.
- [x] 4.2 Run the app on the secondary display and trigger, in a demo repository, a missing base repository, an existing task id, an `index.lock`, a missing Android SDK and an unreadable `.shogunai.json`; check each against the `error-presentation` scenarios, in both languages.

## Workflow follow-up

- Archive the change (`/opsx:archive understandable-errors`) once validated, in the same branch, before opening its pull request against `epic/ui-redesign`.
- With the archive, remove `understandable-errors`'s line from the "Desktop app redesign" list in `docs/roadmap.md` and `docs/roadmap.es.md` (the last change of the epic removes the whole section).
