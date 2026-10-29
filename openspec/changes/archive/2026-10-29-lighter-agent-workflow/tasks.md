# Tasks

## 1. Leaner session-start context

- [x] 1.1 Add an optional session-start text to `WorkflowRule`, used by `sessionStartLines` and ignored by `shogun guide`, and give `resources.desktop-app` its one-line form; verify in `WorkflowRulesTest` that the session lines use the short form and `rulesFor` keeps the full text
- [x] 1.2 Drop the session-start flag from `resources.desktop-turn` and `worktrees.base`; verify in `WorkflowRulesTest` that neither is in the session lines for the main checkout, a worktree and a docker-compose-only repository, and that `shogun guide worktrees` and `shogun guide resources` still print them (`GuideCommandsTest`)
- [x] 1.3 Update the session-start scenarios in `HookCommandsTest` and `HeadlessCommandsTest` to the "Backend session", "Context with the provider" and "Session context" scenarios of the specs, and verify `./gradlew :core:test :cli:test` passes

## 2. Desktop turn for Claude's computer use

- [x] 2.1 Add the list of acting computer-use tools for both prefixes next to `OrcaComputerUse`, with the classification of a tool name and the hook matcher built from it; verify in a unit test that `left_click`, `type`, `key`, `scroll`, `open_application`, `write_clipboard` and `computer_batch` act, and `screenshot`, `zoom`, `cursor_position`, `wait`, `switch_display`, `read_clipboard`, `list_granted_applications` and `request_access` don't, for both prefixes
- [x] 2.2 Make `ClaudeCodeHooks` support several ShogunAi hooks per event (diff, install, status, uninstall), and add the second `PreToolUse` hook with that matcher; verify in `ClaudeCodeHooksTest` the "Several ShogunAi hooks for one event" scenarios and "Desktop turn hook for Claude's computer use"
- [x] 2.3 Make `hook desktop-turn` take, renew or deny the turn for acting computer-use tools, and leave the others alone; verify in `HookCommandsTest` (or `DesktopCommandsTest`) the "Desktop free", "Holder renews", "Linked machine", "Taken", "Others waiting" and "Screenshot while the desktop is taken" scenarios
- [x] 2.4 Update the desktop turn and Claude Code hooks sections of `docs/architecture/cli.md` and `docs/architecture/cli.es.md`, and verify `mkdocs build --strict` succeeds

## 3. Start commit of a new worktree

- [x] 3.1 Print the start commit (ref, short hash and subject) on standard error after `shogun new` creates a new branch, and nothing new with `--branch`; verify in `ShogunCliTest` the "Start commit reported" and "Existing branch" scenarios, and that standard output still has only the path

## 4. Build holding a busy Gradle daemon

- [x] 4.1 Add `HoldingBuild`, `GradleClient` and the session reference to the Gradle daemon model, and parse the latest build entry (start, directory, client port) of the daemon log in `ProcessGradleDaemonSource`; verify in `ProcessGradleDaemonSourceTest` with a log fixture that a busy daemon gets directory and start, and an idle one none
- [x] 4.2 Find the client through the loopback connection (`lsof` as argv, through `ShellCommandExecutor`), its tasks from the process listing, and the agent session by walking its ancestors against the recorded sessions' process ids, each step optional; verify with fakes the "Build started by an agent session", "Client not traceable to a session" and "Connection lookup unavailable" scenarios
- [x] 4.3 Show the holding build under busy daemons in `shogun gradle` and as `holder` in `--json`; verify in `ShogunCliTest` the "Busy daemon held by another session" and "Holder in JSON" scenarios
- [x] 4.4 Document the holding build in the Gradle provider section of `docs/architecture/infrastructure.md` and `docs/architecture/infrastructure.es.md`, and verify `mkdocs build --strict` succeeds

## 5. Integration

- [x] 5.1 Run `./gradlew test` and confirm every module passes without warnings
- [x] 5.2 Install the built CLI's hooks in a scratch Claude settings file and check `shogun hooks status` reads as installed; start a session in a docker-compose-only repository and check the context has no desktop rules; run a long Gradle task in one worktree and check `shogun gradle` from another names the holding build and session

## Workflow follow-up

- Archive the change with `/opsx:archive` before opening the pull request, and update `docs/roadmap.md` and `docs/roadmap.es.md`: add the follow-ups "Show the holding build in the desktop app" and the emulator rule's open question.
