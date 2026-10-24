# Tasks

## 1. Core: building and ending the tour

- [ ] 1.1 Add an optional `environment` map to `ShellCommandExecutor.execute`/`executeWithin`, applied by `ProcessBuilderShellCommandExecutor`; verify with a test that a variable reaches the child process and that existing callers and fakes compile (`./gradlew :core:test`)
- [ ] 1.2 Add `TourLocation` (paths under `~/.shogunai/tour`, `isTour(project)`) and `WorktreeError.TourCreationFailed`; verify with unit tests on a placeholder home
- [ ] 1.3 Add the tour script resource `tour/AGENTS.md` and its rendering with the app language, covering every step listed in design.md; verify with a test that Spanish adds the Spanish line and that the script names every step
- [ ] 1.4 Implement `CreateTourUseCase` (bare remote, sample files, `.shogunai.json`, worktrees in every state, fixed local Git identity, registration with the tour's agents, cleanup on failure, refusal when the folder exists unless replacing); verify with a test against real Git in a temp folder that reads every worktree back with `ReadWorktreeStatusUseCase` and the overview and checks each state from the spec, plus a failing-executor test that leaves no folder and no project
- [ ] 1.5 Implement `EndTourUseCase` (remove worktrees through `RemoveWorktreeUseCase`, delete the project, delete the folder, tolerate what's already gone); verify with tests for a full tour, a folder deleted by hand, and that another project is untouched
- [ ] 1.6 Add `tourOffered` to `JsonSettingsFile.Settings` with its preference accessors; verify with a test that an old settings file reads it as `false`

## 2. Desktop app: entry points and the dialog

- [ ] 2.1 Add `TourState` (steps, tool step using the shared `CommandLineTool`, creation, restart/open-existing choice) and its strings in English and Spanish; verify with state tests for skipping the tool step when available, cancel before creation writing nothing, and the restart path
- [ ] 2.2 Add `TourDialog` with the welcome, tool, creating and open-agent steps, the last one offering the available launchers for the main worktree with the default agent and showing launch errors; verify with a desktop UI test of the step sequence
- [ ] 2.3 Add "Take the tour" to `EmptyState`, Help > "ShogunAi tour" to `AppMenuBar`, and the one-time `TourNotice`; verify with UI tests for the empty state, the menu entry and the notice not returning after dismissal
- [ ] 2.4 Add the tour banner to the tour project's view with "Open the tour's agent" and "End tour" (with confirmation); verify with a UI test that the banner shows only for the tour project and that ending it removes the project from the sidebar
- [ ] 2.5 Run the app with a fresh `~/.shogunai` (placeholder home) and go through the tour end to end: tool step, sample created with every state visible grouped by state, agent opened in the terminal with the prompt, end tour leaving no folder; record what was checked

## 3. Documentation

- [ ] 3.1 Describe the tour in `docs/getting-started.md` and `docs/guide/desktop-app.md` and their `.es.md` translations; verify with `mkdocs build --strict`

## Workflow follow-up

- Archive the change with `/opsx:archive` after validation, updating `docs/roadmap.md` and `docs/roadmap.es.md` (remove item 1 and renumber).
- Open the pull request against `develop`.
