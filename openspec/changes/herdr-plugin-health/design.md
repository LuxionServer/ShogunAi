# Design

## Context

ShogunAi talks to Herdr through the `HerdrClient` port, implemented by `CliHerdrClient`, which runs `herdr` as argv and reads its JSON envelope (`{"id": …, "result": …}` or `{"error": {"message": …}}`). `shogun hooks status` and the app's hooks section (`ClaudeHooksState` and `ClaudeHooksSection` in `ui/shell`) only know about Claude Code's settings today.

Output of `herdr plugin list --json` with Herdr 0.9.5, captured on a real machine:

- Working GitHub install: `result.plugins[]` has `plugin_id: "shogunai.worktrees"`, `enabled: true`, `plugin_root`, `manifest_path` and `source: {kind: "github", owner, repo, subdir, resolved_commit, managed_path, …}`, and no `warnings` key.
- Linked plugin whose folder was deleted: same entry, still `enabled: true`, with `source: {kind: "local"}`, `plugin_root` pointing to the deleted folder, and `warnings: ["manifest unavailable: No such file or directory (os error 2)"]`. The command still exits with code 0. The text output shows it as `[local:<folder>; 1 warning(s)]` with a `warning:` line.

`herdr plugin link`, `unlink`, `uninstall` and `install` print the same JSON envelope; `install` takes `--yes` to skip its own confirmation and `--ref <ref>`.

## Goals / Non-Goals

**Goals:**

- One core use case shared by the command line and the app, with every branch covered by unit tests against `FakeHerdrClient` and fixtures of the real JSON.
- Reinstalling never leaves Herdr with two plugins with the same id.

**Non-Goals:**

- `shogun doctor` and the installation check window don't report the plugin in this change; it is a follow-up in the roadmap.
- Enabling a disabled plugin: the user disabled it, and `herdr plugin enable` is theirs to run.
- Relinking to a local checkout: the reinstall always goes to GitHub, which doesn't depend on any local folder.
- Checking the plugin's version against the app's.

## Decisions

### Broken means "Herdr reports warnings for it"

The plugin is broken when its `warnings` array isn't empty, whatever `enabled` says. Herdr only adds warnings when it can't load the plugin's manifest, so they are the signal Herdr itself gives. Checking whether `plugin_root` exists from ShogunAi was considered and dropped: it would duplicate Herdr's check, miss other manifest errors, and need a file system port in a use case that otherwise only talks to Herdr.

### Model

In `domain/model/Herdr.kt`:

- `HerdrPlugin(id, enabled, sourceKind, root, warnings)` with `sourceKind` as the raw string (`github`, `local`), since only `local` changes behaviour.
- `enum HerdrPluginState { INSTALLED, DISABLED, BROKEN, MISSING, UNKNOWN }`.
- `HerdrPluginStatus(state, plugin: HerdrPlugin?, error: String?)`, where `error` is Herdr's message for `UNKNOWN`.

`HerdrClient` gains `listPlugins()`, `unlinkPlugin(id)`, `uninstallPlugin(id)` and `installPlugin(source)`. They have no default implementations, like the rest of the port, so `FakeHerdrClient` and any test double of the port implement them. The CLI's end-to-end fake `herdr` binary answers `plugin list --json` too, so `hooks status` runs end to end.

### Use case `HerdrPluginHealth`

`HerdrPluginHealth(herdr)` in `domain/usecase`:

- `status(): HerdrPluginStatus?` returns `null` when Herdr isn't installed (`herdr.isInstalled()`), `UNKNOWN` with the message when listing fails with a `WorktreeError`, and the classified state otherwise.
- `reinstallSteps(status): List<HerdrPluginStep>` returns the commands to run: a `Remove` step (`unlink` when the source is `local`, `uninstall` otherwise) when the plugin is broken, then an `Install` step for broken and missing; empty for the other states. The steps are data, so the CLI and the app show the exact Herdr argv before running anything.
- `reinstall(steps): Result<HerdrPluginStatus?>` runs them in order, stops at the first failure and returns it; on success it reads the status again.

The source is the constant `LuxionServer/ShogunAi/integrations/herdr`, the same path the `herdr-integration` spec gives for installing the plugin, without `--ref`, so it installs the default branch as the documented install does.

### Remove before installing

`install` with the same id over a broken entry was not tried, because it would have meant writing to a real Herdr server. Removing first is safe in every case Herdr may have: if the install then fails (no network), the plugin is missing, which `hooks status` and Settings report with an "Install" action, instead of a state we can't predict.

### Command line

- `hooks status` prints a `herdr plugin:` line after the Claude Code ones, followed by indented warning lines and the hint `reinstall it with: shogun hooks install --herdr` for broken and missing. Its JSON adds `herdr: null | {state, source, root, warnings, error}`.
- `hooks install --herdr` is parsed into the existing `CliCommand.Hooks` with a `herdr` flag; `--herdr` with `uninstall`, `status` or `--project` is a usage error. It prints the Herdr commands (`  $ herdr plugin unlink shogunai.worktrees`), asks `Run them? [y/N]` unless `--yes`, and with `--json` prints `{state, steps, applied}`.
- The exit code is 0 when nothing needed doing or the reinstall succeeded, 1 when Herdr isn't installed or a command failed.

### App

A new state holder, `HerdrPluginSettings` (named so to avoid clashing with the domain's `HerdrPluginState`), in `ui/shell`, next to `ClaudeHooksState`, with `refresh()`, `request()`, `confirm()`, `cancel()` and `dismissError()`. The hooks section composes a `HerdrPluginPart(settings)` below the Claude Code part; `PreferencesWindow` refreshes it where it refreshes the hooks. The confirmation dialog reuses `ShogunDialog` and lists the Herdr commands. No file in `ui/worktree` changes.

### Coverage

The new code in `core` and `cli` is covered by unit tests at 100% of lines and branches. No new `@Serializable` class is added (the JSON is read with `JsonObject`), so `core`'s maximum of missed branches stays as it is.

## Risks / Trade-offs

- [Herdr changes the shape of `plugin list --json`] → Missing fields make the entry unreadable and the plugin counts as missing, which is what the user sees and can fix; the fixtures name the Herdr version they come from.
- [Herdr adds warnings that don't stop the plugin from working] → It would be shown as broken and offered a reinstall, which is harmless; the warnings are shown so the user can judge.
- [The reinstall needs network and leaves the plugin missing if the install fails] → Reported with Herdr's error, and the state read again offers "Install".
- [Agents exploring this from a Herdr pane reach the user's real Herdr] → The project's `AGENTS.md` now says to test against fakes and fixtures, never writing to a real Herdr.
