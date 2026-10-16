# Tasks

## 1. Main branch configuration

- [ ] 1.1 Add `mainBranch: String? = null` to `ProjectConfig` and verify with a `JsonProjectRepositoryTest` case that a project stored without the field loads with `mainBranch = null`
- [ ] 1.2 Add the main branch field to `ProjectConfigViewModel` (trimmed, blank saved as `null`, part of `hasUnsavedChanges`) and verify with view model tests for default, set, clear and unsaved-change detection
- [ ] 1.3 Add the "Main branch" field and its placeholder to `ProjectConfigScreen`, with English and Spanish strings, and verify it compiles and the i18n tests pass
- [ ] 1.4 Document `mainBranch` in the `ProjectConfig` section of `docs/architecture/domain.md` and `domain.es.md`, and verify with `mkdocs build --strict`

## 2. Domain: main branch and worktree status

- [ ] 2.1 Add `MainBranch`, `Divergence` and `WorktreeStatus` (with derived `isMerged`) to the domain model and verify with unit tests for `isMerged` (merged, fresh branch, unmerged commits, no main branch)
- [ ] 2.2 Implement `ResolveMainBranchUseCase` and verify with use case tests covering a configured branch with and without its `origin` counterpart, detection from `origin/HEAD`, fallback to local `main` and `master`, and no branch found
- [ ] 2.3 Implement `ReadWorktreeStatusUseCase` and verify with use case tests covering changes and divergence parsing, no upstream, detached HEAD, worktree on the main branch, no commits, and `git status` failing
- [ ] 2.4 Wire both use cases into `WorktreeUseCases` and `AppContainer` and verify the project compiles
- [ ] 2.5 Document the new models and use cases in `docs/architecture/domain.md` and `domain.es.md`, and verify with `mkdocs build --strict`

## 3. UI: statuses in the worktree list

- [ ] 3.1 Add the `statuses` map and `WorktreeStatusState` to `WorktreeViewModel`, loading them after the list (at most 4 at a time, stale loads dropped) and after a create, and verify with view model tests for loading, loaded, unavailable, reload and create
- [ ] 3.2 Add `relativeTime` and its English and Spanish strings and verify with unit tests at each unit boundary in both languages
- [ ] 3.3 Show the status segments, loading indicator and unavailable state in `WorktreeRow`, and verify it compiles
- [ ] 3.4 Document the worktree status in `docs/architecture/ui.md` and `ui.es.md`, and verify with `mkdocs build --strict`

## 4. Integration checks

- [ ] 4.1 Run `./gradlew test` and verify every test passes
- [ ] 4.2 Run the app against this repository with several worktrees (one dirty, one merged, one without upstream, the main worktree) and verify each row shows the expected status, including with `develop` set as main branch
- [ ] 4.3 Run `openspec validate worktree-status --strict` and verify it passes

## Workflow follow-up

- Remove the "Worktree status" bullet from the roadmap's control tower section (both languages) when archiving.
- Archive the change after the pull request to `epic/pivot` is reviewed and merged.
