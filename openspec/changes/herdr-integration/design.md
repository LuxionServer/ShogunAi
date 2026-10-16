## Context

See proposal.md for the motivation. Facts about Herdr 0.9.5 this design relies on, checked against its documentation, JSON schema and source:

- **Plugins** are a folder with a `herdr-plugin.toml` (`id`, `name`, `version`, `min_herdr_version` required; `platforms` recommended). `[[events]]` hooks run `command` as argv, without a shell, with the plugin's folder as working directory, for enabled plugins whenever Herdr emits that event. They're installed from GitHub with `herdr plugin install owner/repo/subdir` or from disk with `herdr plugin link <dir>`.
- **Hook environment**: `HERDR_PLUGIN_EVENT` (e.g. `worktree.created`), `HERDR_PLUGIN_EVENT_JSON` (the serialized event envelope), `HERDR_BIN_PATH`, `HERDR_SOCKET_PATH`, `HERDR_WORKSPACE_ID`, `HERDR_PANE_ID`. `PATH` is inherited from the Herdr server.
- **`worktree.created` envelope**: `{"event": "worktree.created", "data": {"type": "worktree_created", "workspace": {…, "worktree": {"repo_root", "checkout_path", …}}, "worktree": {"path", "branch", "is_linked_worktree", …}}}`. It's emitted after `git worktree add` and after the workspace, tab and pane already exist; the hook runs asynchronously. There's no event before creation, so a plugin can't choose the path or branch.
- **Worktree placement**: `<worktrees.directory>/<repo>/<branch-slug>`, with `[worktrees] directory` in `~/.config/herdr/config.toml` (`%APPDATA%\herdr\config.toml` on Windows), default `~/.herdr/worktrees`.
- **Workspaces**: `herdr workspace create --cwd PATH --label TEXT --env KEY=VALUE… --focus` prints JSON with `.result.workspace.workspace_id` and `.result.root_pane.pane_id`; `--env` sets variables in the new root shell. `herdr workspace list` and `herdr workspace focus <id>` exist. `herdr pane run <pane> "<command>"` submits a command plus Enter atomically. Herdr's docs mention automatically started servers on macOS; whether a CLI call starts one when none is running is checked during implementation (it only changes which error the user sees).
- ShogunAi already has the pieces to reuse: `ProvisionWorktreeUseCase` (what `shogun provision` runs), `ResolveWorktreeEnvironmentUseCase`, `AgentLaunchConfig.resolvedCommand()`, and `ProcessBuilderShellCommandExecutor`, which adds the Homebrew folders to `PATH` for apps launched from Finder.

## Goals / Non-Goals

**Goals:**
- Provisioning of Herdr-created worktrees with no per-repository setup beyond installing the plugin once.
- One click from a ShogunAi worktree to a running agent in Herdr, with the worktree's environment.
- Herdr-created worktrees recognisable in the overview and `shogun status`.

**Non-Goals:**
- Replacing Herdr's worktree creation or choosing its path/branch (Herdr has no pre-creation event).
- Removing worktrees through Herdr, or reacting to `worktree.removed`; `shogun clean` and the overview keep handling cleanup.
- Showing Herdr's agent status (working/blocked/done) in ShogunAi. A natural follow-up, kept out to keep this change small.
- Remote Herdr machines over SSH: the action and the plugin act on the local machine only.
- Publishing the plugin to Herdr's marketplace (adding the `herdr-plugin` topic to the GitHub repository is a manual step outside the code).

## Decisions

### Ship the plugin in this repository, calling the CLI

`integrations/herdr/herdr-plugin.toml` only declares the hook; all logic lives in `shogun hook herdr`, tested with the rest of the CLI. The manifest has no `[[build]]` step and depends only on `shogun` being on the server's `PATH`, which the Homebrew cask and the documented installs provide.

*Alternative:* a separate `herdr-plugin-shogunai` repository with a script that parses the JSON and calls `shogun provision`. Rejected: a second repository to release, and a script runtime (bun, python, jq) that users may not have.

### `shogun hook herdr` reads Herdr's environment, not standard input

It mirrors the Claude Code hooks (`shogun hook <source>`), but Herdr passes the event through `HERDR_PLUGIN_EVENT` / `HERDR_PLUGIN_EVENT_JSON` instead of standard input. The command reads them through the CLI's injectable `environmentVariable` function, so tests don't touch the real environment. It ignores events other than `worktree.created`, so the same command can be bound to more events later without breaking. It then runs the same path as `shogun provision <data.worktree.path>`, so the output and errors are identical, and Herdr's plugin log (`herdr plugin log`) shows them.

### The environment reaches Herdr-created worktrees through the existing channels, not by typing into the pane

The hook could run `herdr pane run $HERDR_PANE_ID 'eval "$(shogun env)"'` to export the variables in the new workspace's shell. Rejected: the hook runs asynchronously after the pane exists, so whatever the user or an automation already started there (an agent, a `pane run`) would receive that text as input. Instead, as for every other agent, the environment arrives through the `envFile` that provisioning writes, Claude Code's `SessionStart` hook, or `direnv` with `eval "$(shogun env)"`. `docs/agents.md` explains this for Herdr.

### "Open in Herdr" creates the workspace with `--env` and then runs the agent

`OpenWorktreeInHerdrUseCase` resolves the worktree's environment, then:

1. `herdr workspace list` and, if a workspace's `worktree.checkout_path` (or its directory) is the worktree, `herdr workspace focus <id>` and stop.
2. Otherwise `herdr workspace create --cwd <worktree> --label <folder name> --env K=V… --focus`, and `herdr pane run <root pane> "<resolved agent command>"`.

Because ShogunAi creates the pane itself, nothing else is typing into it, so running the agent there is race-free, and `--env` puts the variables into the shell itself (shell-agnostic: no `export` syntax to generate for zsh, bash or fish), where they stay for anything run later in that pane.

*Alternative:* `herdr worktree open --cwd <repo> --path <worktree>`, which groups the workspace under the repository in Herdr's sidebar. It has no `--env` option, so the environment would have to be typed into the shell. Kept as an option to revisit if Herdr adds `--env` to `worktree open`, or if Herdr already groups a plain workspace by its Git checkout (to be checked during implementation; it changes nothing in the specs).

Herdr is called through a `HerdrClient` port (domain) implemented in infrastructure on top of `ShellCommandExecutor` with argv lists, parsing its JSON with kotlinx.serialization. A missing binary (`CommandExecutionException`) becomes `WorktreeError.HerdrNotInstalled`; a non-zero exit becomes `WorktreeError.HerdrCommandFailed` with Herdr's stderr.

### Herdr's worktrees folder is read from its config with a minimal parser

`classifyOrigin` gains a `herdrWorktreesRoot` parameter. A `HerdrConfigSource` reads the `directory` key of the `[worktrees]` table from Herdr's `config.toml` with a small line-based reader (table header, `key = "value"`, `~` expansion), falling back to `~/.herdr/worktrees` on any problem. A TOML library would be the robust choice, but it's a new dependency for one key; if Herdr's config ever needs more, switch then. The Claude Code check stays first, then Herdr, then the project's worktrees folder, so a Herdr folder inside the project's worktrees folder still reads as Herdr.

## Risks / Trade-offs

- **Provisioning races the user**: Herdr creates the workspace before the hook runs, so an agent started instantly could build before secrets are copied. → Provisioning is file copies and port reservation, well under a second; documented.
- **`shogun` not on the Herdr server's `PATH`**: when macOS auto-starts the server through launchd its `PATH` may be minimal. → Check during implementation with an auto-started server. The manifest can't search several folders without a shell, so if `shogun` isn't found, document how to fix the server's `PATH` and how to spot the failure in `herdr plugin log`.
- **`herdr` outside the executor's extra `PATH`**: the install script or `cargo install` may put it in `~/.local/bin` or `~/.cargo/bin`, which a Finder-launched app doesn't see. → `HerdrClient` looks for the binary in those folders too before reporting it as not installed.
- **Herdr's JSON output changes**: Herdr is pre-1.0. → Parse only the fields used, ignore unknown keys, set the plugin's `min_herdr_version` to the version verified, and keep a fixture-based test of the parsed responses.
- **Workspace duplicated when the directory doesn't match exactly** (symlinks, trailing slash). → Compare canonical paths, as the CLI already does for worktrees.

## Migration Plan

Additive: users without Herdr see one more row action that reports Herdr isn't installed. No stored data changes.
