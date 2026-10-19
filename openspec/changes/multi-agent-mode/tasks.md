# Tasks

## 1. Core

- [ ] 1.1 Add `AgentLaunchConfig.name`, `ProjectConfig.additionalAgents`, `ProjectConfig.agents` and the display name rule; add repository tests reading an old project and round-tripping several agents, and verify they pass
- [ ] 1.2 Make the copy, terminal and Herdr launch use cases take the agent to use, defaulting to the project's default; update their tests and add one per use case with a second agent, and verify they pass
- [ ] 1.3 Pass every agent command to `AgentSearch` and detection; add a detection test with a second agent's executable and verify it passes
- [ ] 1.4 Add `Settings.worktreeAgents` with backward-compatible reading, clearing on default, fallback for removed agents and pruning of missing worktrees; add tests and verify they pass

## 2. Desktop app

- [ ] 2.1 Turn the Agent section of project settings into a list of agents with add, remove, reorder, "Make default" and name validation; add view-model tests for each spec scenario and verify they pass
- [ ] 2.2 Add the agent picker to the detail's launch buttons, the "Launch with" submenu to the row context menu and the per-agent palette entries, hidden with one agent; add UI tests and verify they pass
- [ ] 2.3 Add the "Agent" field to the worktree's detail to set its agent without launching; add a UI test and verify the launch actions follow it
- [ ] 2.4 Add the strings in English and Spanish, document agents in `docs/guide/desktop-app.md`, `docs/guide/configuration.md` and their Spanish versions, and verify `StringsTest` and `mkdocs build --strict` pass

## 3. Validation

- [ ] 3.1 Run `./gradlew test` and verify every module passes
- [ ] 3.2 In the running app (secondary display), add Codex to a project, launch a worktree with it, check the next launch uses Codex and that a Codex process is detected; remove Codex and check the worktree falls back to Claude

## Workflow follow-up

- Archive the change before opening its pull request.
