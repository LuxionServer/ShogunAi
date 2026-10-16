# Tasks

## 1. Main branch configuration

- [x] 1.1 Add `mainBranch: String? = null` to `ProjectConfig` and verify with a `JsonProjectRepositoryTest` case that a project stored without the field loads with `mainBranch = null`
- [x] 1.2 Add the main branch field to `ProjectConfigViewModel` (trimmed, blank saved as `null`, part of `hasUnsavedChanges`) and verify with view model tests for default, set, clear and unsaved-change detection
- [x] 1.3 Add the "Main branch" field and its placeholder to `ProjectConfigScreen`, with English and Spanish strings, and verify it compiles and the i18n tests pass
- [x] 1.4 Document `mainBranch` in the `ProjectConfig` section of `docs/architecture/domain.md` and `domain.es.md`, and verify with `mkdocs build --strict`

## 2. Domain: main branch and worktree status

- [x] 2.1 Add `MainBranch`, `Divergence` and `WorktreeStatus` (with derived `isMerged`) to the domain model and verify with unit tests for `isMerged` (merged, fresh branch, unmerged commits, no main branch)
- [x] 2.2 Implement `ResolveMainBranchUseCase` and verify with use case tests covering a configured branch with and without its `origin` counterpart, detection from `origin/HEAD`, fallback to local `main` and `master`, and no branch found
- [x] 2.3 Implement `ReadWorktreeStatusUseCase` and verify with use case tests covering changes and divergence parsing, no upstream, detached HEAD, worktree on the main branch, no commits, and `git status` failing
- [x] 2.4 Wire both use cases into `WorktreeUseCases` and `AppContainer` and verify the project compiles
- [x] 2.5 Document the new models and use cases in `docs/architecture/domain.md` and `domain.es.md`, and verify with `mkdocs build --strict`

## 3. UI: statuses in the worktree list

- [x] 3.1 Add the `statuses` map and `WorktreeStatusState` to `WorktreeViewModel`, loading them after the list (at most 4 at a time, stale loads dropped) and after a create, and verify with view model tests for loading, loaded, unavailable, reload and create
- [x] 3.2 Add `relativeTime` and its English and Spanish strings and verify with unit tests at each unit boundary in both languages
- [x] 3.3 Show the status segments, loading indicator and unavailable state in `WorktreeRow`, and verify it compiles
- [x] 3.4 Document the worktree status in `docs/architecture/ui.md` and `ui.es.md`, and verify with `mkdocs build --strict`

## 4. Integration checks

- [x] 4.1 Run `./gradlew test` and verify every test passes
- [x] 4.2 Run the app against this repository with several worktrees (one dirty, one merged, one without upstream, the main worktree) and verify each row shows the expected status, including with `develop` set as main branch
- [x] 4.3 Run `openspec validate worktree-status --strict` and verify it passes

## Workflow follow-up

- Remove the "Worktree status" bullet from the roadmap's control tower section (both languages) when archiving.
- Archive the change after the pull request to `epic/pivot` is reviewed and merged.
