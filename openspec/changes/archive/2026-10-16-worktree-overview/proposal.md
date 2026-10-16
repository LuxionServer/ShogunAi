# Proposal

## Why

Agents now create worktrees on their own (`claude --worktree` puts them under `.claude/worktrees/`), so a repository ends up with worktrees from several origins that nobody keeps track of. The list already shows their Git status and can clean up merged ones, but it can't tell where a worktree came from, whether an agent is still working in it, how much disk it takes, or that it has been abandoned. This is the last piece of the control tower layer in the roadmap.

## What Changes

- Each worktree shows its **origin**: created by ShogunAi (inside the project's worktrees folder), by Claude Code (inside `.claude/worktrees/`), or elsewhere.
- Each worktree shows the **AI agents running in it**, detected from the processes whose working directory is inside it (Claude, Codex, Gemini, Cursor agent, OpenCode, Aider, Copilot, and the project's own agent command). Detection uses `lsof` and `ps`; where they aren't available (Windows) nothing is shown.
- Each worktree shows its **disk usage**, computed in the background without counting nested worktrees, and the list header shows the total.
- A worktree with no uncommitted changes, no running agent and no commit in the last 14 days is flagged as **inactive**.
- All of it is read again whenever the list reloads.

## Capabilities

### New Capabilities
- `worktree-overview`: identifying each worktree's origin, the agents running in it, its disk usage and whether it is inactive, and showing them in the worktree list.

### Modified Capabilities

## Impact

- Domain: new `WorktreeOrigin`, `RunningAgent` models; `classifyOrigin` and `isInactive` functions; `DetectRunningAgentsUseCase` (runs `lsof -d cwd -F pcn` once per load and `ps -o pid=,comm=` for the matching processes) and `MeasureWorktreeSizeUseCase`; `FileManager` gains `directorySize(path, excluding)`.
- Infrastructure: `NioFileManager.directorySize` walks the tree without following symlinks.
- UI: `WorktreeViewModel` gains per-worktree agents and sizes; `WorktreeRow` shows origin, agents, size and the inactive label; the header shows the total size; new strings in English and Spanish.
- Docs: domain, infrastructure and UI architecture pages in both languages; the roadmap item is removed when the change is archived.
