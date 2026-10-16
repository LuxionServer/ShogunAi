# Tasks

## 1. Domain and infrastructure

- [ ] 1.1 Add `WorktreeOrigin` and `classifyOrigin` and verify with unit tests for Claude Code (also inside the worktrees folder), ShogunAi, other location, the main worktree and trailing slashes
- [ ] 1.2 Add `RunningAgent` and `DetectRunningAgentsUseCase` and verify with use case tests for an agent in a worktree, an agent in a nested worktree, non-agent processes, the project's own agent command, duplicate processes, and `lsof` or `ps` failing
- [ ] 1.3 Add `directorySize` to `FileManager`, implement it in `NioFileManager` and `FakeFileManager`, and verify with an infrastructure test on a temporary folder covering exclusions and symlinks
- [ ] 1.4 Add `MeasureWorktreeSizeUseCase` and verify with use case tests for nested exclusions and a missing folder
- [ ] 1.5 Add `isInactive` and verify with unit tests for the 14-day threshold, uncommitted changes, running agents and no last commit
- [ ] 1.6 Wire the new use cases into `WorktreeUseCases` and `AppContainer` and verify the project compiles
- [ ] 1.7 Document the new models, functions, use cases and the `FileManager` method in `docs/architecture/domain.md`, `infrastructure.md` and their Spanish versions, and verify with `mkdocs build --strict`

## 2. UI

- [ ] 2.1 Add agents, sizes and `totalSize` to `WorktreeViewModel` and verify with view model tests for loading, detection unavailable, reload and stale results
- [ ] 2.2 Add `formatSize` and verify with unit tests for each unit boundary
- [ ] 2.3 Show origin, agents, size, the inactive label and the header total in the worktree screen, with English and Spanish strings, and verify it compiles and the tests pass
- [ ] 2.4 Document the overview in `docs/architecture/ui.md` and `ui.es.md`, and verify with `mkdocs build --strict`

## 3. Integration checks

- [ ] 3.1 Run `./gradlew test` and verify every test passes
- [ ] 3.2 Run the app against this repository while this session works in a Claude Code worktree and verify the origin, the running agent, the sizes (main excluding the nested worktree) and the header total
- [ ] 3.3 Run `openspec validate worktree-overview --strict` and verify it passes

## Workflow follow-up

- Archive the change right after validation, in the same branch and before opening the pull request, removing the "Full overview" bullet from the roadmap (both languages).
