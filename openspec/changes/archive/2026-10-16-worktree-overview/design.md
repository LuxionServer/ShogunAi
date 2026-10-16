# Design

## Context

`WorktreeViewModel` loads the list, then per-worktree statuses (`statuses`) and the main branch, discarding stale results with `listGeneration`. `ProjectConfig` holds `worktreesRoot` and `agentLaunchConfig.agentCommand`. Shell commands go through `ShellCommandExecutor`; filesystem access through the `FileManager` port. Claude Code creates worktrees under `<repository>/.claude/worktrees/<name>`, which is inside the main worktree's folder. On macOS, `lsof` reports the Claude Code process name as its version (e.g. `2.1.301`), while `ps -o comm=` reports `claude` or the full path of its binary. Requirements are in `specs/worktree-overview/spec.md`.

## Goals / Non-Goals

**Goals:**
- Origin, running agents, disk usage and inactivity per worktree, without slowing down the list.
- Correct attribution with nested worktrees, both for processes and for sizes.

**Non-Goals:**
- Identifying origins other than ShogunAi and Claude Code; other tools' default paths aren't reliable enough to name them.
- Agent detection on Windows. The data simply doesn't show there.
- Acting on running agents (stopping them, focusing their terminal).

## Decisions

### Origin is a pure function of the path

`classifyOrigin(worktree, config)` returns `WorktreeOrigin.CLAUDE_CODE` if the path contains `/.claude/worktrees/`, `SHOGUNAI` if it is inside `config.worktreesRoot`, `OTHER` otherwise, and `null` for the main worktree. Claude Code is checked first because `.claude/worktrees` can be inside the worktrees folder (it is when the worktrees folder is the repositories' parent). Paths are compared after normalizing separators and trailing slashes, so it works on Windows too.

### Agents come from one `lsof` and one `ps` per load

`DetectRunningAgentsUseCase(config, executor)` takes the listed worktrees and returns `Result<Map<String, List<RunningAgent>>>` keyed by worktree path:

1. `lsof -d cwd -F pcn` lists every process with its working directory (`p<pid>`, `c<command>`, `n<path>` lines). It takes a few tens of milliseconds, so one call per load is enough.
2. Each process is assigned to the worktree with the longest path that is equal to or a parent of its working directory; processes outside every worktree are dropped.
3. `ps -o pid=,comm= -p <pids>` gives the executable for the remaining processes. The command name from `lsof` isn't usable because Claude Code renames its process to its version.
4. The executable's file name is matched against the known agents (`claude`, `codex`, `gemini`, `cursor-agent`, `opencode`, `aider`, `copilot`) plus the file name of the first word of the project's agent command. Each agent appears once per worktree.

If either command fails (no `lsof` on Windows, permissions), the use case returns a failure and the ViewModel shows no agent information. *Alternative considered*: one `ps` per worktree or reading `/proc`. Rejected: `/proc` doesn't exist on macOS, and per-worktree calls multiply processes for no gain.

### Disk usage through `FileManager.directorySize`

`FileManager.directorySize(path, excluding: Set<String>): Long` walks the tree with `Files.walkFileTree`, without following symlinks, adding regular file sizes and skipping any directory in `excluding` and files it can't read. `MeasureWorktreeSizeUseCase(fileManager)` calls it on `Dispatchers.IO` for one worktree, excluding the paths of every other listed worktree that is nested inside it, and returns `null` for a missing folder. Going through the port keeps it cross-platform (no `du`) and testable with `FakeFileManager`.

### ViewModel state

`WorktreeViewModel` adds `agents: Map<String, List<RunningAgent>>?` (`null` when detection isn't available) and `sizes: Map<String, Long>`, both reset on each load and guarded by `listGeneration`. Agents are detected once after the list loads. Sizes are measured one worktree at a time in a single coroutine so large trees don't compete with status reads. `totalSize` is the sum of the sizes measured so far. `isInactive(status, agents, now)` is a pure domain function: no uncommitted changes, no agents, last commit older than 14 days; the row evaluates it with `Instant.now()`.

### Row layout

The branch line gains, after the branch name and the existing "locked" label, the origin ("ShogunAi", "Claude Code" or "other location") in muted text, and the running agents (e.g. "● claude") in the success color. The status line gains the size as its last segment and an "inactive" label. The "Worktrees" header shows "N worktrees · X GB" next to the title. Sizes use binary units with one decimal (`formatSize`: B, KB, MB, GB, TB), labeled as KB/MB/GB as most desktop tools do.

## Risks / Trade-offs

- [Walking very large worktrees (dependency folders, build outputs) takes time] → It runs in the background, one worktree at a time, and never blocks the list or the statuses.
- [An agent started through a wrapper (e.g. `headroom wrap claude`) may show up as the wrapper] → The project's own agent command is also matched, and the real agent process is usually a child with its own working directory, so it is still found.
- [Process names other than the known ones aren't detected] → The list covers the common CLIs and the project's own command; more can be added later without changing the approach.
- [`lsof` may not see processes of other users] → Only the user's own agents matter here.
