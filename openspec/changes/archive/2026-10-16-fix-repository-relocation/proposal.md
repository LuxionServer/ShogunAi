## Why

When the base repository is moved on disk and the user updates its path in the project configuration, several things keep pointing to the old location:

- Secret files entered as absolute paths (e.g. pasted by hand) keep pointing to the old repository, and `ProjectConfig.baseSecretPath` joins them incorrectly with the new base path.
- `worktreesRoot` isn't updated even when the worktrees lived inside or next to the base repository (e.g. `~/projects/main-repo` → `~/projects`).
- Git stores absolute paths in both directions between the base repository and its worktrees, so moving the repository (and the worktrees with it) breaks the links and `git worktree list` keeps showing old paths.
- The worktree screen caches its `ViewModel` by project id, so after editing the project it keeps using use cases built with the old configuration.

## What Changes

- Absolute `secretFiles` entries inside the base repository are normalized to relative paths when the project is loaded into the form and when they're added, so they follow the repository when it moves.
- Changing the base repository path in the form also moves `worktreesRoot` when it was inside the repository, its parent or a sibling folder, unless the user edited it by hand.
- New `RepairWorktreesUseCase` that runs `git worktree repair` from the base repository, passing the new path of the worktrees that moved along with `worktreesRoot`.
- When an existing project is saved with a different base path or `worktreesRoot`, the worktree screen repairs the links before listing.
- The form and worktree screen `ViewModel`s are also keyed by the configuration, not only by the project id.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `project-configuration`: secret files and the worktrees root follow the base repository when its path changes.
- `worktree-workspace`: worktrees are repaired after moving the repository and the screen always uses the current configuration.

## Impact

- `domain/usecase/RepairWorktreesUseCase.kt` (new) and its wiring in `AppContainer`/`WorktreeUseCases`.
- `ui/projectconfig/ProjectConfigViewModel.kt`, `ui/projectconfig/RepositoryRelocation.kt` (new), `ui/FilePicker.kt`.
- `ui/AppRoot.kt`, `ui/Screen.kt`, `ui/worktree/WorktreeViewModel.kt`.
- Tests: `RepairWorktreesUseCaseTest`, `ProjectConfigViewModelTest` (new) and `WorktreeViewModelTest`.
