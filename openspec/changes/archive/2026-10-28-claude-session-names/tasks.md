# Tasks

## 1. Claude Code's session registry

- [x] 1.1 Add the `ClaudeSession` model (`pid`, `cwd`, `name`, optional `startedAt`), the `ClaudeSessionRegistry` port and a `FakeClaudeSessionRegistry` in test fixtures; verify the core module compiles without warnings
- [x] 1.2 Implement `FileClaudeSessionRegistry(directory)`: read only `*.json`, lenient parsing with nullable fields, skip files without `pid`/`cwd` or that can't be read or parsed, missing directory as empty, never write; verify `FileClaudeSessionRegistryTest` in a temporary directory with a valid file, a file with unknown and missing fields, an invalid JSON file, a `.key` file and a missing directory
- [x] 1.3 Build the registry directory from `CLAUDE_CONFIG_DIR` or the user home at the composition roots (`ShogunCli`, `AppContainer`), reusing how `ClaudeCodeHooks.userSettingsPath` resolves the config dir; verify a unit test of the path resolution with and without the variable

## 2. Naming rules

- [x] 2.1 Add the session name builder (lowercase, runs outside `[a-z0-9]` to one hyphen, trim, join parts, 64-character cap) and the names for a group member, a single-repository worktree, a main worktree and the multirepo agent; verify unit tests for every scenario of "Name the Claude sessions ShogunAi launches" and "Characters outside the set" in `specs/claude-session-names`
- [x] 2.2 Add `namedClaudeCommand(command, name)`: accept `claude`, a path ending in `/claude`, `headroom wrap claude …`, leading `NAME=value` and `env`; reject other agents, `-n`/`--name`/`--name=`, shell control operators and words merely containing `claude`; verify unit tests for every scenario of "Only a plain Claude command gets a name"
- [x] 2.3 Add the uniqueness step (first free of `name`, `name-2`, `name-3`… against live sessions' names); verify unit tests for a free name, a name taken by a live session and a name only held by a dead session

## 3. Naming and reporting in `open --herdr`

- [x] 3.1 Extend `HerdrOpening` with `sessionName: String?` and `HerdrAgentLauncher` with an optional registry and an `isClaudeProcessAlive` function (default: `ProcessHandle` with a command ending in `claude`/`claude.exe`); `launch` takes the session name and folder, types the named command, and after the agent check polls for a live session with that name in that folder, leaving `null` on timeout without failing; verify `HerdrAgentLauncherTest` with a fake registry for: name confirmed, name never confirmed, non-Claude command typed unchanged, uniqueness suffix, no registry
- [x] 3.2 Make `focusLiveAgent` take the folder and report the live session in it (expected name first, otherwise most recently started); verify launcher tests for focused with one session, with several, and with none
- [x] 3.3 Pass the session name and folder from `OpenWorktreeInHerdrUseCase` (project name from the base repository folder, `isMain` handled), `OpenGroupInHerdrUseCase` (`<alias>-<group.title>`) and `OpenMultirepoAgentInHerdrUseCase` (multirepo name); verify `OpenWorktreeInHerdrUseCaseTest` and `GroupStateAndContextTest` assert the typed commands (two members of the same group get different `-n` values, `codex` gets none) and the reported names for opened, started-agent and focused
- [x] 3.4 Report the name in `herdrOpeningLine` and as `session_name` in `openJson`, `groupOpenJson` and `multirepoAgentOpenJson`, and wire the registry into the CLI's `herdrLauncher()` and the desktop app's open use cases; verify `ShogunCliTest` and `MultirepoCommandsTest` cases for `open --json` with a name, the already-open case and `null`, and the text line
- [x] 3.5 Document the session names of `shogun open --herdr` (text and `session_name`) in `docs/guide/cli.md`/`.es.md` and `docs/guide/multirepo.md`/`.es.md`, including that coordinating sessions can message group members by name; verify `mkdocs build --strict` passes

## 4. Session names in `status`

- [x] 4.1 Add `ClaudeSessionRef(name, pid)` and `sessions` to `RunningAgent`; inject the registry into `DetectRunningAgentsUseCase`, keep sessions with a name whose `pid` is in `claudePids`, attribute them with `attributedWorktree` and attach them, sorted, to that worktree's `claude` agent, never failing on a registry error; verify `DetectRunningAgentsUseCaseTest` with two worktrees of the same group and a fake registry: each worktree gets its own session, several sessions in one worktree, a subfolder, a nested repository, a `cwd` outside every worktree, a dead pid and a throwing registry
- [x] 4.2 Show the names in `agentLabel` (`claude(working, a, b)`, `claude(a)`, `claude(working)`) and add `sessions: [{name, pid}]` to `agentJson`; wire the registry into the CLI's and the desktop app's `DetectRunningAgentsUseCase`; verify `ShogunCliTest` and `MultirepoCommandsTest` status cases for the text label and the JSON in a repository and in a multirepo folder, and that every existing field is unchanged
- [x] 4.3 Document the session names in `shogun status` (text and `sessions`) in `docs/guide/cli.md`/`.es.md` and `docs/guide/multirepo.md`/`.es.md`, and the new port in `docs/architecture/domain.md`/`.es.md`; verify `mkdocs build --strict` passes

## 5. Integration

- [x] 5.1 Run `./gradlew test` and verify every module passes without warnings
- [x] 5.2 Open a real group of two repositories with `shogun open <id> --herdr` and verify the two Claude sessions get `<alias>-<id>` names, `shogun open <id> --herdr --json` reports them as focused, and `shogun status` in the multirepo folder shows each name on its repository's line

## Workflow follow-up

- Archive the change with `/opsx:archive` once implemented and validated, before opening the pull request, updating `docs/roadmap.md` and `docs/roadmap.es.md` in the same branch.
- Open the pull request against `develop`, assigned to CiroFeliu.
