# Tasks

## 1. Orca metadata in the port and adapter

- [ ] 1.1 Record fixtures from Orca 1.4.243 into `core/src/test/resources/orca/`: a `worktree set` response (including the `UNKNOWN_WORKSPACE_STATUS` warning shape) and a `worktree ps` row with `comment`, `workspaceStatus`, `linkedPR` and `linkedIssue` set, with placeholder paths. Verify: `grep -r "$HOME" core/src/test/resources/orca` is empty.
- [ ] 1.2 Add `comment`, `workspaceStatus`, `linkedPullRequest` and `linkedIssue` to `OrcaWorktree`, parsed from `worktree ps` and `worktree list`, and `OrcaClient.updateWorktree(path, OrcaWorktreeUpdate)` running `orca worktree set --worktree path:<abs>` with only the non-null flags; implement it in `CliOrcaClient` and `FakeOrcaClient`. Verify: `CliOrcaClientTest` asserts the parsed fields from the fixtures and the exact argv for each combination of fields, and an `ok: false` answer becomes `OrcaCommandFailed`.
- [ ] 1.3 Extend `OrcaWorkspaceStore` and `JsonOrcaWorkspaceStore` with the last board status written per worktree (`boardStatuses` in `~/.shogunai/orca.json`), keeping `workspacesRoot` intact. Verify: store tests that both fields survive each other's writes and that an old file without `boardStatuses` reads.

## 2. GitHub lookups

- [ ] 2.1 Add the `GitHubClient` port and `CliGitHubClient`: `pullRequestFor(directory, branch)` (`gh pr list --head <branch> --state all --limit 1 --json number`) and `issueExists(directory, number)` (`gh issue view <n> --json number`), found with `ExecutableLocator`, 10 s limit, any failure or missing `gh` meaning no result; plus `FakeGitHubClient`. Verify: `CliGitHubClientTest` with a fake executor asserts argv, working directory, parsing, and null/false on a non-zero exit, a timeout and a missing binary.

## 3. The sync

- [ ] 3.1 Add `orcaBoardStatus(activity)` and `orcaComment(activity, status, agents)` in core. Verify: unit tests for every row of the mapping table in `design.md` and for the comment's parts (state, agent waiting, uncommitted, not pushed, ahead/behind the base, merged into the base, singular and plural).
- [ ] 3.2 Implement `OrcaWorktreeSync`: match items to rows by canonical path, skip the main worktree, folder workspaces and unmapped states, write the status only when the mapped one differs from the remembered one (or, first time, from Orca's), write the comment only when Orca's is blank or starts with `ShogunAi: ` and differs, link the pull request for pushed branches and the issue from the branch name only when Orca has none, throttle lookups to one per worktree every 10 minutes, send one `set` per worktree with only the changed fields, prune remembered statuses on a full sync and return failures instead of throwing. Verify: `OrcaWorktreeSyncTest` with `FakeOrcaClient` and `FakeGitHubClient` covers every scenario of the delta spec except "When ShogunAi syncs a worktree to Orca" and "A failed sync never fails the action".

## 4. Callers

- [ ] 4.1 Desktop app: give `WorktreeBoard` a `syncOrca` callback called at the end of `checkProjects()` with the loaded worktrees' items, expose the reader's last rows in `OrcaWorktreeReader`, and wire it in `AppContainer` so nothing runs when Orca isn't running. Verify: `WorktreeBoard` test that a check hands the loaded worktrees' activities to the callback; `OrcaWorktreeReader` test that the last rows are null after a failed or not-running read; scenario "Orca not running".
- [ ] 4.2 CLI: after `shogun open <target> --orca` succeeds and after `shogun hook orca setup` provisions, sync that worktree; print `shogun: couldn't update <path> in Orca: <reason>` on a failure without changing the exit code. Verify: CLI tests for "Opening from the command line", "A worktree created from Orca" and "Orca rejects the update".
- [ ] 4.3 Document the sync in `docs/agents.md`, `docs/guide/cli.md` and `docs/architecture/` (new port, store field), with Spanish translations. Verify: `mkdocs build --strict`.

## 5. Validation

- [ ] 5.1 Run `./gradlew test`. Verify: all modules pass with no warnings.
- [ ] 5.2 Check against real Orca 1.4.243: `shogun open <id> --orca` on a worktree of a disposable or this repository sets its status and comment; a pushed branch with a pull request gets it linked; a comment typed in Orca is kept; a card moved by hand stays until the state changes (with the desktop app running). Restore the worktree's metadata afterwards.

## Workflow follow-up

- Archive with `/opsx:archive`, removing this item from `docs/roadmap.md` and `docs/roadmap.es.md`.
- Implementation commit, rebase on `origin/develop`, pull request against `develop` assigned to CiroFeliu.
