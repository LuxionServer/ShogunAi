# Tasks

Prerequisite: `orca-integration` is implemented in this branch's base, or merged into it before starting (see proposal.md).

## 1. Orca port: folder workspaces

- [ ] 1.1 Record fixtures from Orca 1.4.243 on a disposable sample multirepo into `core/src/test/resources/orca/`, with placeholder paths: a `worktree ps` with a folder workspace (with and without an agent), a `folder` repository and a Git main worktree; `terminal list --worktree id:folder:<id>`; `terminal create` in a folder workspace; and `repo add` rejecting a non-Git folder. Then remove the sample from Orca. Verify: `grep -r "$HOME"` over the fixtures is empty; Orca's `repo list`, project groups and folder workspaces match their state before recording.
- [ ] 1.2 Replace the path in `OrcaClient`'s terminal methods with `OrcaWorkspace` (`Worktree(path)` → `path:<abs>`, `Folder(id)` → `id:folder:<id>`), parse `workspaceKind`/`worktreeId` from `ps` into `OrcaWorktree`, and close a folder workspace's terminals by handle. `OpenWorktreeInOrcaUseCase` and `CloseOrcaTerminalsUseCase` pass `Worktree(path)`. Verify: `CliOrcaClientTest` asserts the argv of both selectors and the parsed folder rows; the existing Orca tests still pass; `./gradlew :core:test` with no warnings.
- [ ] 1.3 Add `WorktreeError.OrcaMultirepoFolderNotOpen(folder)` with its English and Spanish message, giving the two ways to add the folder in Orca's app. Verify: i18n key parity test; `ErrorPresentation` test for the message.

## 2. Use cases

- [ ] 2.1 Implement `OpenGroupInOrcaUseCase`: check that Orca is installed once, then open each member in order with `OpenWorktreeInOrcaUseCase`, its project's agent and its group session name, collecting a result per member. Verify: `OpenGroupInOrcaUseCaseTest` with `FakeOrcaClient` covers the scenarios "Open a group", "Partly open", "Repositories grouped in Orca", "One worktree fails" and "Orca not installed" of "Open a group's worktrees in Orca".
- [ ] 2.2 Implement `OpenMultirepoAgentInOrcaUseCase`: start Orca if needed, find the workspaces whose canonical path is the multirepo folder (live agent first, then Orca's order), register a Git folder Orca doesn't know, otherwise fail with `OrcaMultirepoFolderNotOpen`, then focus or start the agent with the multirepo's session name and wait 10 s for it. Verify: tests for every scenario of "Open the multirepo agent in Orca" and "Host the multirepo agent in Orca", including `/tmp` vs `/private/tmp`, and a test that nothing is registered for a non-Git folder.
- [ ] 2.3 Map the agents of an Orca folder-workspace row to the multirepo agent in `DetectRunningAgentsUseCase` (exactly the multirepo folder), with Orca's `waiting` as blocked. Verify: a test for "Agent managed by Orca", and one that an agent in a member repository's folder isn't the multirepo agent.

## 3. Command line

- [ ] 3.1 Allow `--orca` in the multirepo folder: with a target, open the group (`--agent` exits 2, ambiguous id 2, unknown 1); without one, open the multirepo agent; the unapproved declaration behaves as outside a multirepo. Remove `orca-integration`'s multirepo usage error. Verify: CLI tests for the exit codes and text lines of both requirements.
- [ ] 3.2 Write the JSON of both commands (`branch`/`repos` with `terminal`; `path`/`orca`/`terminal`/`agent`/`session_name`) and update the help text. Verify: CLI tests for the "Result as JSON" scenarios; `./gradlew :cli:run --args="open --help"` mentions the multirepo forms with `--orca`.

## 4. Desktop app

- [ ] 4.1 Add `openGroupInOrca` and `openAgentInOrca` to `MultirepoActions` and their Orca notices, and "Open all in Orca" and "Open multirepo agent in Orca" next to the Herdr actions, shown only when Orca is installed. Strings in English and Spanish. Verify: desktop tests for "Open all in Orca", "Open all in Orca with a failure" and every scenario of "Open the multirepo agent in Orca from the app"; i18n key parity.
- [ ] 4.2 Mark group worktrees with Orca terminals in the "Clean up group" dialog, reusing `orca-integration`'s lookup. Verify: a desktop test for "Clean up a group open in Orca", and that it behaves as before with Orca not running.
- [ ] 4.3 Add the runner selector ("Don't open", "Herdr", "Orca", each runner only when installed, hidden with neither) to "New change", and open the created worktrees in the chosen runner, showing each repository's opening result after its creation result. Verify: desktop tests for every scenario of "New change from the app".
- [ ] 4.4 Check it in the real app on a disposable sample multirepo, with Orca on the secondary screen: "Open all in Orca", "New change" with Orca, and the multirepo agent in a Git multirepo folder and in a folder workspace created in Orca. Remove the sample from Orca afterwards. Verify: the agents start in Orca under their repositories and in the folder, the multirepo view shows the multirepo agent's Orca state, and Orca's state matches its state before the check.

## 5. Documentation

- [ ] 5.1 Document opening groups and the multirepo agent in Orca, the one-time setup of a non-Git multirepo folder in Orca's app (import as a group plus a folder workspace, or "Open as Folder"), and the "New change" selector, in `docs/guide/multirepo.md`, `docs/agents.md`, `docs/guide/cli.md` (commands and the `open` JSON row) and `docs/guide/desktop-app.md`, with their `.es.md` translations. Verify: `mkdocs build --strict`.
- [ ] 5.2 Document the `OrcaWorkspace` target in `docs/architecture/` (the page `orca-integration` adds the port to) and its Spanish translation. Verify: `mkdocs build --strict`.

## 6. Validation

- [ ] 6.1 Run the whole suite and the spec validation. Verify: `./gradlew test` passes with no warnings; `openspec validate orca-multirepos --strict` passes.

## Workflow follow-up

- On archiving, remove the multirepo step from item 1 of `docs/roadmap.md` and `docs/roadmap.es.md`.
- Archive before opening the pull request, on a base that contains `orca-integration`.
