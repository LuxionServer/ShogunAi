# Tasks

Blocks A (sections 1–6) are the core and the command line and can be validated and used on their own; block B (sections 7–9) is the desktop app on top of them.

## 1. A · Multirepo declaration, registry and configuration

- [x] 1.1 Add `MultirepoDeclaration`, `MultirepoRegistry` and the `MultirepoSource` port with its JSON implementation for `.shogunai-multirepo.json` and `~/.shogunai/multirepos.json` (relative and absolute paths, `agent` as `AgentLaunchConfig`, atomic writes, no lock file next to the declaration, never inside a repository) with typed `WorktreeError`s; verify unit tests for every scenario of "Declare a multirepo", "Reject an invalid multirepo declaration" and "Know the multirepos of the machine"
- [x] 1.2 Add `ResolveMultirepoUseCase` (walk up for the declaration, else the known multirepo that declares the repository's main worktree); verify unit tests from the multirepo folder, a repository outside it, a worktree anywhere and a stale registry entry
- [x] 1.3 Add the use cases behind `shogun multirepo init|show|add-repo|remove-repo|defaults|agent|worktrees-root|delete`, linking or registering projects by canonical path, and the CLI subcommands with `--json` and help; verify CLI tests for every scenario of "Create a multirepo from the command line", "Configure a multirepo from the command line", "Reuse the projects already registered", "Propose a worktrees folder per repository", "Delete a multirepo" and "Show a multirepo from the command line", including that `projects.json` keeps its format and an existing project is unchanged
- [x] 1.4 Document multirepos and `shogun multirepo` in a new `docs/guide/multirepo.md` and in `docs/guide/cli.md`, with their `.es.md` translations and the `mkdocs.yml` nav; verify `mkdocs build` passes

## 2. A · Groups by branch

- [x] 2.1 Add `DetectGroupsUseCase` (worktrees of every repository keyed by their full branch name, groups in two or more repositories and ungrouped); verify unit tests for every scenario of "A group is a branch shared by repositories", including worktrees created outside ShogunAi in unrelated folders and different prefixes

- [x] 2.2 Skip agent attributions that cross a nested Git repository in `DetectRunningAgentsUseCase`; verify unit tests for the modified worktree-overview scenarios, including an agent in a nested repository
- [x] 2.3 Treat a multirepo folder that is the top level of a repository as the multirepo in its main checkout and as a repository in its linked worktrees; verify unit and CLI tests for every scenario of "A multirepo folder that is a repository"

## 3. A · Group creation

- [x] 3.1 Add `CreateGroupUseCase` over the existing `CreateWorktreeUseCase` and `CreateWorktreeFromBranchUseCase` with each project's config (aliases from `--repos` or `defaultRepos`, existing worktrees on the branch reported as `existing`, new branch from `origin/<base>` or existing local branch, optional fetch, per-repository rollback); verify unit tests for "Create a group from the command line", "Adding repositories to a group is idempotent" and "A failing repository doesn't undo the others"
- [x] 3.2 Add `shogun new --repos`, the group/single-worktree dispatch, the JSON result and help text; verify CLI tests for every scenario of "Where a group is created from", unknown alias, options that don't apply, and "JSON result of group creation"
- [x] 3.3 Document group creation and adoption of existing worktrees in `docs/guide/multirepo.md` and `docs/guide/cli.md` and their translations; verify `mkdocs build` passes

## 4. A · Status and agent context

- [x] 4.1 Add `ReadGroupsUseCase` (per worktree status against its base, agents with one `AgentSearch` per repository, ungrouped worktrees); verify unit tests including a repository whose status is unavailable
- [x] 4.2 Add the group output of `shogun status` and `--json` in the multirepo folder and the changes to "Find the repository and its project"; verify CLI tests for every scenario of "Show groups from the command line" and the modified command-line-tool scenarios
- [x] 4.3 Add `GroupContext` (group block, multirepo block, OpenSpec detection) and use it in `shogun hook session-start`; verify unit and CLI tests for "Tell a group worktree's agent about its group", "Tell the multirepo agent about the multirepo", the running-session scenario of "Grouping never changes a worktree" and the modified SessionStart scenarios
- [x] 4.4 Document status and the agents' context in `docs/guide/multirepo.md` and `docs/guide/cli.md` and their translations; verify `mkdocs build` passes

## 5. A · Opening in Herdr

- [x] 5.1 Add `createWorkspace(cwd, label)` and `listPanes()` to `HerdrClient`, `CliHerdrClient` and `FakeHerdrClient`; verify `CliHerdrClient` tests with a fake executor for the argv and the parsing of Herdr's responses
- [x] 5.2 Add opening a group (id or branch resolution, each worktree through `OpenWorktreeInHerdrUseCase`, failures per repository) and `OpenMultirepoAgentInHerdrUseCase` (find by pane cwd, else create and run the agent); verify unit tests for "Open a group's worktrees in Herdr" and "Open the multirepo agent in Herdr"
- [x] 5.3 Add both to `shogun open` in the multirepo folder (`<id>` and no target), rejecting `--agent` for a group; verify CLI tests for every scenario of both requirements
- [x] 5.4 Document opening in `docs/guide/multirepo.md` and `docs/guide/cli.md` and their translations; verify `mkdocs build` passes

## 6. A · Group cleanup and compatibility

- [x] 6.1 Add `SelectGroupCleanupUseCase` (candidates and kept groups with reasons); verify unit tests for every scenario of "Select groups to clean up"
- [x] 6.2 Add the group path of `shogun clean` and `--yes` (per repository the existing cleanup with `--delete-branches` and the provider flags); verify CLI tests for every scenario of "Remove a group whole" and the JSON output
- [x] 6.3 Add compatibility tests: a machine with projects and worktrees and no multirepo behaves as before and its `~/.shogunai` files are byte-identical after every command; existing worktrees with matching branches, including ones created outside ShogunAi, are grouped with unchanged paths, names, ports, Compose projects and Herdr workspaces; worktrees created with today's `shogun new` in `<worktreesRoot>/app/<id>` and `<worktreesRoot>/api/<id>` before the multirepo existed are adopted as a group without moving or renaming them and with no question about the worktrees folder; a registered project enters a multirepo unchanged; a deleted multirepo leaves projects and worktrees as they were; verify they pass with `./gradlew test`
- [x] 6.4 Document cleanup and the compatibility guarantees in `docs/guide/multirepo.md` and `docs/guide/cli.md` and their translations; verify `mkdocs build` passes
- [x] 6.5 Validate block A: `./gradlew test`, `openspec validate multirepo-groups --strict`, and by hand on a throwaway multirepo of three local repositories with bare remotes and pre-existing worktrees: `multirepo init/add-repo/defaults/agent`, `new` with defaults and `--repos` (worktrees landing in `<worktreesRoot>/<alias>/<id>` after accepting the proposed folders), `status`, `open <id> --herdr`, `open --herdr`, a Claude Code session in a group worktree and in the multirepo folder, `clean` and `multirepo delete`; verify each matches its spec

## 7. B · Sidebar and multirepo view

- [x] 7.1 Add `MultirepoCatalog` to the desktop app (loaded with the projects, refreshed with them) and the sidebar folder with nested projects, summed activity and remembered collapse; verify desktop UI tests for "Multirepo folder in the sidebar", "Multirepo activity in the sidebar" and the modified app-shell scenarios
- [x] 7.2 Add the multirepo view with the `BY_CHANGE` grouping, group titles from the branch, the alias column, the "Ungrouped" block, the state grouping alternative and the combined state; verify unit tests for the combined state and desktop UI tests for "Multirepo view grouped by change" and "Group header with a combined state"
- [x] 7.3 Add the header actions ("Open all in Herdr", "Add repository", "Clean up group" with its confirmation) and "Open multirepo agent in Herdr"; verify desktop UI tests for "Group header actions" and "Open the multirepo agent from the app"

## 8. B · Group tags and headers in the other views

- [x] 8.1 Add the group tag to `WorktreeRowModel` and the name cell, navigating to the multirepo view with the group selected, and the `multirepo / project` header in project grouping; verify desktop UI tests for "Group tag in other views" and the modified worktree-row and worktree-views scenarios

## 9. B · Adding and configuring a multirepo

- [x] 9.1 Add the "Add multirepo" dialog (drop target and folder picker, detected repositories with aliases, repositories from elsewhere, existing projects marked, proposed worktrees folder per repository, groups detected by branch) writing through the same use cases as the CLI; verify desktop UI tests for every scenario of "Add a multirepo from the app"
- [x] 9.2 Add the multirepo settings screen (repositories, defaults, proposed worktrees folder, agent with the existing agent editor, delete); verify desktop UI tests for every scenario of "Multirepo settings"
- [x] 9.3 Add the "New change" dialog over `CreateGroupUseCase`; verify desktop UI tests for "New change from the app"
- [x] 9.4 Add the strings of sections 7–9 in every supported language and document the app's multirepo screens in `docs/guide/desktop-app.md` and `docs/guide/multirepo.md` with translations; verify the localization tests and `mkdocs build` pass
- [x] 9.5 Validate block B: `./gradlew test` and, in the running app, add the throwaway multirepo by dropping its folder, check the sidebar, the multirepo view, the tags, the settings, "New change" and "Clean up group"; verify each matches its spec
