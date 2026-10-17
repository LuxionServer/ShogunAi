## Context

See proposal.md for the motivation. Facts about Herdr 0.9.5 this design relies on, checked against its documentation, JSON schema, source and a Herdr server run in isolation:

- **Worktrees in Herdr**: `herdr worktree open --cwd <repo> --path <worktree> [--label] [--focus]` opens an existing worktree as a workspace grouped under the repository's space (the workspace carries `worktree.repo_key`), and creates the repository's workspace if needed. Its JSON has `.result.workspace.workspace_id`, `.result.root_pane.pane_id` and `.result.already_open`; for an already open worktree it returns that workspace with `already_open: true`. It has no `--env` option. `herdr worktree create` (and the "New worktree" button) always places the checkout at `<[worktrees] directory>/<repo>/<branch-slug>`, which can't follow ShogunAi's `<worktreesRoot>/<name>` convention.
- **Panes**: `herdr pane split <pane> --direction right --cwd PATH --env KEY=VALUE… --focus` starts a new shell with those variables (`.result.pane.pane_id`); `herdr pane close <pane>` closes one; `herdr pane run <pane> "<command>"` submits a command plus Enter atomically. Pane ids change when panes are moved, so nothing stores them.
- **Server**: CLI calls don't start a server; with none running they print `{"error": {"code": "server_not_running", "message": "… run `herdr` to start or attach it"}}` and exit with `1`. Errors are written as JSON on standard error, not standard output (e.g. `pane_not_found` too). When macOS starts the server through launchd, the plist carries the whole environment of the shell that ran `herdr`, `PATH` included (`launchd_spawn.rs`).
- **Plugins**: a folder with `herdr-plugin.toml` (`id`, `name`, `version`, `min_herdr_version`; `platforms`). `[[events]]` hooks run `command` as argv, without a shell, in the plugin's folder, with the server's environment plus `HERDR_PLUGIN_EVENT`, `HERDR_PLUGIN_EVENT_JSON`, `HERDR_BIN_PATH`, `HERDR_SOCKET_PATH`. The `worktree.created` envelope is `{"event": "worktree.created", "data": {"type": "worktree_created", "workspace": {…}, "worktree": {"path", "branch", …}}}`, emitted after the workspace and pane exist; the hook runs asynchronously, and there's no event before creation. `herdr notification show <title> --body TEXT` shows a toast.
- **Herdr's config**: `config.toml` at `HERDR_CONFIG_PATH`, else `$XDG_CONFIG_HOME/herdr/`, else `%APPDATA%\herdr\` on Windows, else `~/.config/herdr/`; worktrees folder in `[worktrees] directory`, default `~/.herdr/worktrees`.
- ShogunAi already has: `ProvisionWorktreeUseCase` (behind `shogun provision`), `ResolveWorktreeEnvironmentUseCase` (behind `shogun env`, including provider contributions such as the emulator's `ANDROID_SERIAL`), `AgentLaunchConfig.resolvedCommand()`, and `ProcessBuilderShellCommandExecutor`, which adds the Homebrew folders to `PATH` for apps launched from Finder.

## Goals / Non-Goals

**Goals:**
- ShogunAi creates every worktree, in the project's worktrees folder; Herdr only opens them, grouped under the repository like its own "Open worktree…".
- One click from a ShogunAi worktree to a running agent in Herdr, with the worktree's full per-worktree environment.
- Worktrees created from Herdr by mistake still end up provisioned, and stand out (Herdr origin, notification).

**Non-Goals:**
- Making Herdr's "New worktree" follow ShogunAi's convention or intercepting it (Herdr has no pre-creation event or path template). A Herdr action that creates the worktree through ShogunAi and then opens it is a possible follow-up.
- Removing worktrees through Herdr, or reacting to `worktree.removed`.
- Showing Herdr's agent status in ShogunAi (roadmap).
- Remote Herdr machines over SSH.
- Publishing the plugin to Herdr's marketplace (a manual `herdr-plugin` topic on the repository).
- A `shogun` command to open in Herdr; the desktop app is the entry point for now.

## Decisions

### "Open in Herdr" uses `worktree open`, then swaps its first pane for one with the environment

`OpenWorktreeInHerdrUseCase`:

1. `herdr worktree open --cwd <base repository> --path <worktree> --label <folder name> --focus`. Herdr groups it under the repository's space; if `already_open`, the workspace is now focused and the use case returns `FOCUSED` without touching its panes.
2. It resolves the worktree's environment with `ResolveWorktreeEnvironmentUseCase` (reserving ports if needed), the same source as `shogun env`, so every variable of `env` and of the providers arrives, including future ones such as an iOS simulator's.
3. If the environment isn't empty, `herdr pane split <root pane> --direction right --cwd <worktree> --env K=V… --focus` and `herdr pane close <root pane>`: the workspace keeps a single pane whose shell was started with the variables. Then `herdr pane run <that pane> "<resolved agent command>"` and `OPENED`.

ShogunAi has just created those panes, so nothing else is typing into them and running the agent is race-free; `--env` sets the variables in the shell itself (shell-agnostic, no `export` syntax per shell) and they stay for anything run later in that pane.

*Alternatives:* `herdr workspace create --cwd <worktree> --env …` (the first implementation) gets the environment in one call but the workspace isn't grouped under the repository and Herdr doesn't recognise it as a worktree, so detecting it again needed matching pane directories. Typing `export …` into the root pane after `worktree open` avoids the extra pane but depends on the shell's syntax. `pane split` + `pane close` keeps grouping, Herdr's own `already_open` detection and shell independence.

Herdr is called through a `HerdrClient` port implemented on `ShellCommandExecutor` with argv lists. A binary that can't start becomes `WorktreeError.HerdrNotInstalled`; a non-zero exit or an `error` in the JSON becomes `WorktreeError.HerdrCommandFailed` with Herdr's `error.message`, read from the JSON on standard output or standard error (or the raw stderr when it isn't JSON). The binary is looked up in `~/.local/bin` and `~/.cargo/bin` before the `PATH`, since a Finder-launched app doesn't see them. The action doesn't need `shogun` on the `PATH`: the app calls `herdr` directly.

### The plugin stays as a rescue path, and flags what it rescues

ShogunAi is where worktrees are created, but a worktree created from Herdr's "New worktree" (or by an agent through Herdr's API) still exists and still needs its files. The plugin keeps provisioning those through `shogun hook herdr`; it's cheap and already isolated in its own command. After provisioning, when the repository has a ShogunAi project and the worktree is outside its worktrees folder, the hook runs `$HERDR_BIN_PATH notification show ShogunAi --body "<folder> is outside <worktreesRoot>; create worktrees from ShogunAi"` so the user learns about the convention where the mistake happened. A failed notification never fails the hook.

The manifest only declares the hook; all logic is in the CLI and tested there. It depends on `shogun` being on the `PATH` of the shell that started Herdr's server.

*Alternatives:* drop the plugin (worktrees created from Herdr would come out without secrets or environment file and nothing would tell the user); move the worktree to the project's folder from the hook (Herdr has already opened a workspace and pane in the old path, so moving it under them would break the session).

### `shogun hook herdr` reads Herdr's environment, not standard input

It mirrors the Claude Code hooks, but Herdr passes the event through `HERDR_PLUGIN_EVENT` / `HERDR_PLUGIN_EVENT_JSON`, read through the CLI's injectable `environmentVariable`. Other events are ignored, so more can be bound later. It provisions through the same path as `shogun provision <data.worktree.path>`, so output and errors match and `herdr plugin log` shows them.

### The environment of Herdr-created worktrees isn't typed into their pane

The hook could run `herdr pane run $HERDR_PANE_ID 'eval "$(shogun env)"'`, but it runs asynchronously after the pane exists, so an agent or command already started there would receive that text. The environment of those worktrees arrives through the `envFile`, Claude Code's `SessionStart` hook or `direnv`; the recommended flow avoids the case altogether.

### Herdr's worktrees folder is read from its config with a minimal parser

`classifyOrigin` takes an optional `herdrWorktreesRoot`; `HerdrConfigSource` reads `[worktrees] directory` with a small line reader (table header, basic or literal string, comments, `~`), falling back to `~/.herdr/worktrees`. A TOML library is a new dependency for one key. Order: Claude Code, Herdr, the project's folder, other. With ShogunAi creating every worktree, this origin only marks the misplaced ones.

## Risks / Trade-offs

- **`shogun` not on the Herdr server's `PATH`** (the case on the author's machine today): the plugin's hook fails with "not found" in `herdr plugin log`. → The rescue path documents installing `shogun` on the `PATH` (Homebrew cask) and restarting Herdr's server; the main path ("Open in Herdr") doesn't depend on it.
- **Provisioning races the user on the rescue path**: Herdr opens the workspace before the hook runs. → File copies and port reservation take well under a second; documented.
- **The swapped pane flickers**: on open, the first pane is briefly replaced. → Only when the worktree has an environment; acceptable for a one-off action.
- **Herdr's CLI output changes** (pre-1.0). → Parse only used fields, ignore unknown ones, `min_herdr_version = "0.9.5"`, fixtures recorded from Herdr 0.9.5 in tests.
- **A worktree opened before this change with `workspace create`** isn't recognised by `already_open`. → It opens a second, grouped workspace once; harmless.

## Migration Plan

Additive: users without Herdr see one more row action that reports Herdr isn't installed. No stored data changes.
