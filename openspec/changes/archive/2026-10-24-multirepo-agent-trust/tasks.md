# Tasks

## 1. Registry: approvals and local agent

- [x] 1.1 Extend `MultirepoSource` with `isApproved(folder, subject)`, `approve(folder, subject)`, `agent(folder)` and `setAgent(folder, agent)`, and `JsonMultirepoSource` with the `local` map keyed by declaration path (SHA-256 of the subject, agent), under `LocalFileLock`, file mode `600` on POSIX, `forget` dropping the entry; update `FakeMultirepoSource`. Verify with `JsonMultirepoSourceTest`: approve/isApproved round trip, another subject not approved, agent round trip, forget clears both, a registry written by the old format still lists its folders, and an unknown `local` entry shape doesn't break reading.
- [x] 1.2 Add `Multirepo.approved`, `Multirepo.localAgent` and `Multirepo.approvalSubject` (normalized folder, aliases with resolved paths sorted, resolved worktrees root, declared workspace setting); `Multirepo.agent` reads `localAgent` or the `claude` default and never the declaration. Verify with model tests: subject unchanged by `defaultRepos` or a declared `agent`, changed by a repository path, `worktreesRoot`, workspace setting or folder.

## 2. Resolution and configuration

- [x] 2.1 `ResolveMultirepoUseCase`: `invoke` only returns registered, approved multirepos (the upward walk matches registered folders only); add `nearestDeclaration(directory)`; `known()` sets `approved`/`localAgent`; `declaring()` uses approved ones only. Reject repository paths and `worktreesRoot` with control or Unicode formatting characters in `validate`. Verify with `ResolveMultirepoUseCase` tests for every scenario of "Only approved multirepos apply", "Know the multirepos of the machine" and "Control characters in a path".
- [x] 2.2 Add `WorktreeError.MultirepoNeedsReview` (exit code 1). `ConfigureMultirepoUseCase`: `init`, `addRepository` and `update` refuse an unapproved multirepo, write the declaration without `agent`, and approve the written subject; `setAgent` writes only the local setting; add `trust` and `revoke`. Verify with `ConfigureMultirepoUseCaseTest`: writes keep approval, write refused on a changed declaration, declared `agent` dropped on save, `setAgent` leaves the file unchanged, revoke keeps the file.
- [x] 2.3 `OpenMultirepoAgentInHerdrUseCase` runs the local agent. Verify with a test where the declaration's `agent` holds a marker command: the fake Herdr receives `headroom wrap claude` and never the marker.
- [x] 2.4 Update `docs/architecture/domain.md`, `docs/architecture/infrastructure.md` and their `.es.md` (local agent, approval subject, registry format).

## 3. Command line

- [x] 3.1 `ShogunCli`: print the ignored-declaration note on stderr from non-hook commands when `invoke` returns nothing and `nearestDeclaration` finds an unapproved one; hooks never print it. Verify with CLI tests: `status`, `new` and `open --herdr` under an unregistered declaration behave as outside a multirepo with the note, `hook session-start` there prints no multirepo context nor note, and the PoC declaration (`touch …; claude`) never reaches the fake Herdr.
- [x] 3.2 `shogun multirepo trust [folder] [--yes | --revoke]`: review output with outside-the-parent marks, terminal confirmation defaulting to no, exit 1 without a terminal and without `--yes`, invalid and missing declarations; add it to `CliHelp`. Verify with CLI tests for every scenario of "Review and approve a multirepo from the command line" and "Revoke a multirepo from the command line".
- [x] 3.3 `shogun multirepo show` (text and `--json`) reports `approved`, `ignoredDeclaredAgent` and the local agent, and works under an unapproved declaration; writing subcommands report `MultirepoNeedsReview`; `multirepo agent` stores locally; `open --herdr` prints the local agent's name. Verify with `MultirepoCommandsTest` for the modified scenarios of "Configure", "Show" and "Open the multirepo agent in Herdr", and the upgrade scenario (registered without approval).
- [x] 3.4 Update `docs/guide/multirepo.md`, `docs/guide/cli.md` and their `.es.md`: the agent is local, declarations must be approved with `shogun multirepo trust`, what the approval covers and when it's asked again, the upgrade step.

## 4. Desktop app

- [x] 4.1 `AppContainer` and the sidebar: unapproved registered multirepos are marked as needing review and don't group their repositories; the multirepo view shows the review with "Trust" and disables group actions, "New change" and "Open multirepo agent in Herdr". Verify with `WorktreeBoardTest`/view-model tests (unapproved multirepo, trust restores groups) and by running the app with a multirepo whose declaration was edited by hand.
- [x] 4.2 Add-multirepo dialog: for a folder with an existing declaration, list its repositories with resolved paths, worktrees root and workspace, marking paths outside the parent; confirming registers and approves. Verify with `MultirepoSetupTest` and by hand dropping a folder with a declaration that points outside it.
- [x] 4.3 Multirepo settings save the agent locally, show the ignored declared agent and refuse to save while the multirepo needs review; `WorktreeBoard` searches the multirepo agent by the local command. Verify with `MultirepoSettingsState` tests and by hand (agent changed, `.shogunai-multirepo.json` unchanged).
- [x] 4.4 Update `docs/guide/desktop-app.md`, `docs/architecture/ui.md` and their `.es.md`.

## 5. Integration

- [x] 5.1 Run `./gradlew test` (no CI on the advisory fork) and reproduce the advisory's PoC with `./gradlew :cli:installDist`, an isolated `user.home` and a fake `herdr`: the cloned declaration is ignored with the note, `shogun multirepo trust --yes` approves it, `open --herdr` then runs `headroom wrap claude`, and a later edit of `repos` makes it ask again.
- [x] 5.2 Update `docs/roadmap.md` and `docs/roadmap.es.md` if the change completes or adds items.

## Workflow follow-up

- Archive the change (`/opsx:archive`) in this branch before opening the pull request.
- Push only to the advisory's private fork (`git push ghsa-multirepo fix/multirepo-agent-trust`) and open the pull request there against `develop` (`gh pr create --repo LuxionServer/ShogunAi-ghsa-5c7x-q846-43h3 --base develop --assignee CiroFeliu`); never push to `origin` or open anything on the public repository.
