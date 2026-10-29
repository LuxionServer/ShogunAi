# Tasks

## 1. Claude Code's session registry

- [ ] 1.1 Add `ClaudeSessionStatus` (`BUSY`, `WAITING`, `IDLE`) and `status`/`statusUpdatedAt` to `ClaudeSession`, with a `state` property mapping them to `AgentState` (idle is `DONE` only when the status changed more than 5 s after `startedAt`). Parse both fields in `FileClaudeSessionRegistry`, with unknown or missing status as `null`. Verify: `FileClaudeSessionRegistryTest` covers each status, an unknown one and a missing one; a model test covers the idle rule, including missing timestamps.
- [ ] 1.2 Add `liveSessionsIn(folder)` and `terminalOf(session)` to `ClaudeSessionNaming`, the latter through an optional `ProcessEnvironmentReader` reading `ORCA_TERMINAL_HANDLE`. Verify: `ClaudeSessionNamingTest` (or the Orca use case tests) with a fake reader.

## 2. Process environment port

- [ ] 2.1 Add the `ProcessEnvironmentReader` port and `SystemProcessEnvironmentReader`: `ps -E -ww -o command= -p <pid>` as argv on macOS, `/proc/<pid>/environ` on Linux, `null` on Windows and on any failure. Add `FakeProcessEnvironmentReader` to the test fixtures. Verify: adapter tests with `FakeShellCommandExecutor` for the argv and the token parsing (present, missing, failed command) and a temporary `environ` file for Linux.

## 3. Opening in Orca

- [ ] 3.1 In `OrcaAgentLauncher.focusLiveAgent`, when no terminal reports an agent, focus the workspace terminal whose handle a live session of the folder carries (expected name first, then newest), reporting `focused` with that session's name. Verify: `OpenWorktreeInOrcaUseCaseTest` covers "Orca lost track of the agent", "Session outside Orca" and "Terminal unknown"; `OpenGroupInOrcaUseCaseTest` covers "Multirepo agent".
- [ ] 3.2 In `OrcaAgentLauncher.start`, end the wait when Orca reports the agent, or a live session of the folder has the new session's name or the new terminal's handle. Verify: tests for "Agent only in the registry" (by name and by handle) and "Agent never detected".
- [ ] 3.3 Pass `SystemProcessEnvironmentReader` where the CLI and the desktop app build `ClaudeSessionNaming` for Orca. Verify: `./gradlew build` with no warnings.

## 4. Agent states

- [ ] 4.1 In `DetectRunningAgentsUseCase.withClaudeSessions`, give a `claude` agent without a state the most urgent registry state of its worktree's live sessions, with source `CLAUDE_CODE`. Verify: `DetectRunningAgentsUseCaseTest` covers every scenario of "Show the state Claude Code's session registry reports".

## 5. Documentation and validation

- [ ] 5.1 Update `docs/agents.md`, `docs/guide/installation.md` and their Spanish translations: what happens with Orca's hooks off and what ShogunAi falls back to, and the registry as the last state source. Verify: both languages say the same.
- [ ] 5.2 Run `./gradlew test`. Verify: everything passes with no warnings.
- [ ] 5.3 Validate with Orca 1.4.243 on the disposable repository, with the user's permission to turn Orca's hooks off and restart Orca: a Claude that outlived the restart is focused by `shogun open --orca` instead of a second one, a new open succeeds, and `shogun status` shows the registry state for the lost agent. Turn the hooks back on afterwards and remove the repository from Orca. Verify: `~/.claude/settings.json` matches its state before, and `orca agent hooks status` says enabled.
