# Design

## Context

After `orca-integration` and `orca-multirepos`, both runners are complete but reached through parallel code:

- **Ports.** `HerdrClient` (workspaces and panes) and `OrcaClient` (repositories, worktrees and terminals) speak each tool's vocabulary. They stay as they are: they're the adapters' own clients, and the other Orca changes in flight add methods to `OrcaClient`.
- **Use cases.** Each runner has its own use cases to open a worktree (`OpenWorktreeInHerdrUseCase`, `OpenWorktreeInOrcaUseCase`), a group (`OpenGroupInHerdrUseCase`, `OpenGroupInOrcaUseCase`) and the multirepo agent (`OpenMultirepoAgentInHerdrUseCase`, `OpenMultirepoAgentInOrcaUseCase`), each with its own outcome (`HerdrOpening`, `OrcaOpening`, `GroupOpening`, `GroupOrcaOpening`).
- **Callers.** Every caller branches on the runner: `ShogunCli.open` and `openInMultirepo`, the JSON writers (`openJson`, `groupOpenJson`, `groupOrcaOpenJson`, `multirepoAgentOpenJson` twice each), `MultirepoActions` (four lambdas), `NewChange`'s `ChangeRunner`/`RunnerOutcome` and `WorktreeBoard.openCreated`.

Herdr is hard-wired as the default in:

- the Worktree menu item and Cmd+E (`openSelectedInHerdr`);
- the tour's last step (`TourAgentChoices`);
- `WorkflowRules` (`parallel.open` says `--herdr`, conditioned on Herdr answering);
- `GroupContext.multirepoLines`.

Settings live in `~/.shogunai/settings.json` (`JsonSettingsFile`), which the CLI already reads for resource limits.

## Goals / Non-Goals

**Goals:**
- Choose Herdr or Orca once, in Settings, and have the app's default actions, `shogun open`, the guide and the session-start hook follow it.
- One runner port for opening, with no change in behaviour, messages, exit codes or JSON output for either runner.
- A bounded diff that leaves `HerdrClient`, `OrcaClient` and the per-runner use cases untouched, since the other Orca changes edit them.

**Non-Goals:**
- Merging `HerdrClient` and `OrcaClient`, or rewriting the use cases on top of a common low-level port. Their models differ (workspace with panes vs. repository with terminals), as `orca-integration` found.
- Putting removal (closing Herdr workspaces and Orca terminals) and agent states behind the port. They run for every installed runner whatever the choice, so they gain nothing from it.
- Hiding the runner that isn't chosen. Both stay available everywhere they are today.
- A CLI command to set the runner. Agents read it; the person chooses it in Settings.

## Decisions

### `RunnerId` and the runner in use
`RunnerId { HERDR, ORCA }`, with its display name and its `shogun open` flag (`herdr`, `orca`); see below for where it lives.

`runnerInUse(chosen: RunnerId?, installed: (RunnerId) -> Boolean): RunnerId`:
1. The chosen runner when it's installed.
2. Otherwise the first installed of Herdr and Orca, in that order.
3. Otherwise the chosen runner, or Herdr when nothing is chosen, so errors keep naming a runner.

This mirrors `ToolsPreferences.terminalShown` and matches the epic's `defaultRunner`, which gives no runner instead of the third case. Someone with only Herdr, or with nothing chosen and Herdr installed, sees no change.

*Alternative:* the runner in use is always the chosen one, Herdr by default. Rejected: a person with only Orca would get a shortcut that fails.

### Stored in `settings.json`, in the shape the UI redesign epic uses
The `epic/ui-redesign-v2` branch already stores a default runner for its action catalog. This change uses the same model so the two merge with small conflicts:
- `RunnerId { HERDR, ORCA }` with its display name, `@Serializable`, in `Tools.kt`; the `shogun open` flag is an extension (`RunnerId.flag`), so the enum's declaration matches the epic's.
- `Settings.runner: RunnerId? = null`, and `loadRunner()`/`saveRunner()` on `ToolPreferenceRepository`, implemented by `JsonToolPreferenceRepository` over `JsonSettingsFile`.
- `DetectedTools.runners`, filled by `LocalToolDetector` from an `isRunnerInstalled` check.
- `ToolsPreferences.chosenRunner`, `installedRunners`, `defaultRunner` and `chooseRunner`, and the "Default runner" choice first in Settings' Tools section.
The CLI reads the same repository. An unreadable file reads as no choice.

### One `AgentRunner` port over the existing use cases
In `domain/io`:

```kotlin
interface AgentRunner {
    val id: RunnerId
    fun isInstalled(): Boolean
    suspend fun openWorktree(config: ProjectConfig, worktree: Worktree, agent: AgentLaunchConfig): Result<RunnerOpening>
    suspend fun openGroup(group: WorktreeGroup): Result<List<GroupRunnerOpening>>
    suspend fun openMultirepoAgent(multirepo: Multirepo): Result<RunnerOpening>
}
```

- **Outcome.** `RunnerOpening(runner, outcome: RunnerOutcome, handle, agent, sessionName)` with `RunnerOutcome { OPENED, STARTED_AGENT, FOCUSED }`. `handle` is Herdr's pane id or Orca's terminal handle. `GroupRunnerOpening(alias, path, result)` replaces `GroupOpening` and `GroupOrcaOpening` at the callers.
- **Adapters.** `HerdrAgentRunner` and `OrcaAgentRunner` in `domain/usecase`. Each builds the runner's existing use cases and maps its outcome; the use cases don't change.
  - Herdr's group open can't fail as a whole today, so its adapter always returns success.
  - Orca's group open keeps its single "not installed" check.
- **Installed check.** `HerdrClient` gains `fun isInstalled(): Boolean`, which `CliHerdrClient` already implements, with a default of `true` so fakes don't change.
- **Callers.** The CLI's `open` and `openInMultirepo`, and the desktop app's `MultirepoActions`, take an `AgentRunner` by `RunnerId` (a `Map<RunnerId, AgentRunner>` built once in `ShogunCli` and `AppContainer`) instead of branching.

*Alternative:* make `OpenWorktreeIn*UseCase` implement the port directly. Rejected: it changes the classes the other Orca changes edit, for no behavioural gain.

### Output stays byte for byte
The JSON writers take `RunnerOpening` and choose the runner's keys from `opening.runner`:
- Herdr: `herdr` and `pane_id`, plus `opened` for the multirepo agent.
- Orca: `orca` and `terminal`.
`RunnerOutcome.jsonValue` gives the same strings as today. The text lines (`opened … in Herdr with …`, `focused …'s running agent in Orca …`) move into one `openingLine(path, opening)` with a branch per runner. The CLI tests that pin today's output are the check.

### `shogun open` without a runner flag
`CliCommand.Open` gets `runner: RunnerId?` instead of `herdr`/`orca` booleans. `--herdr` or `--orca` set it, and passing both stays a usage error. Without either, `open` uses the runner in use. In the CLI, a runner counts as installed when its binary is found: `CliHerdrClient.isInstalled()` or `OrcaClient.isInstalled()`.

### The guide and the session-start hook name the runner in use
- **Context.** `WorkflowContext.herdrAvailable` becomes `runner: RunnerId` and `runnerAvailable: Boolean`.
- **Condition.** `WorkflowCondition.HERDR` becomes `RUNNER`, labelled "when the runner in use is available".
- **Rule text.** `WorkflowRules.forRunner(runner)` returns the catalog with `parallel.open` saying `shogun open <id> --<flag>`. `WorkflowRules.all` stays the Herdr catalog for the tests that list every rule. `rulesFor` and `sessionStartLines` use `context.runner`.
- **Availability.** Herdr is available when it answers, with today's probe (`listAgents` within the timeout). Orca is available when it's installed: `shogun open --orca` starts it, and probing its app at every session start would be slow.
- **Plain guide.** `shogun guide --plain` uses the runner in use without probing.
- **Multirepo folder.** `GroupContext.multirepoLines(multirepo, runner)` names `shogun open <id> --<flag>`.

### Desktop app
- **State.** `ToolsPreferences` holds the runner too, as in the epic: the chosen runner, the installed runners (from the tool detection, off the UI thread) and `defaultRunner`, the runner in use. It's already refreshed at start-up and when Settings opens, and already reaches every project's view, so the runner needs no wiring of its own.
- **Settings.** It's shown first in the Tools section of Settings, "Default runner", listing the installed runners. When none is installed it says so, as for terminals.
- **Default actions.**
  - `WorktreeBoard.openSelectedInRunner()` opens the selection in the runner in use, and the Worktree menu item is labelled with that runner, keeping Cmd+E / Ctrl+E.
  - `TourAgentChoices` offers "Open in <runner in use>".
  - The detail pane, the row's context menu, a group's header and the multirepo agent's menu list the runner in use first and the other after it.
  - `NewChange` starts its selector on the runner in use when it's offered, and on "Don't open" otherwise.
- **Worktree opens keep their per-runner use cases.** `ProjectWorktrees.openInHerdr` and `openInOrca` keep their state, Herdr's shortcut of focusing an agent the overview already knows, and their messages, so their tests and the code the other Orca changes touch stay as they are. They gain a common `openIn(runner, worktree, picked)`, used by the menu, the shortcut, the tour and the ordered buttons.
- **Multirepo actions go through the port.** This is where the app branched on the runner. `MultirepoActions` replaces `openAgent`, `openAgentInOrca`, `openGroupInHerdr` and `openGroupInOrca` with:
  - `openGroup(runner, group)` and `openAgent(runner, multirepo)`, both returning the port's outcome;
  - `installedRunners: Set<RunnerId>`.
  `NewChange`'s `ChangeRunner`/`RunnerOutcome` become a `RunnerId?` (null is "Don't open") and `RunnerOpening`. `MultirepoNotice.AgentOpened` carries the runner and replaces `AgentOpenedInOrca`; the texts stay per runner.

## Risks / Trade-offs

- [The other Orca changes edit `OrcaClient`, `CliOrcaClient`, `FakeOrcaClient` and `Strings`] → This change doesn't touch the Orca client or its fake, and only adds strings. Conflicts are resolved when rebasing on `develop` before the pull request.
- [Refactoring the callers could change output] → The existing CLI and desktop tests that pin messages and JSON stay unchanged, and the adapters get tests that map every outcome.
- [Orca counted as available only because it's installed] → The guide then says `--orca` even while Orca is closed, which is correct, since `shogun open --orca` starts it.
- [People with only Orca installed and no choice see the default move to Orca] → That's intended: today their shortcut fails.

## Migration Plan

Additive. `settings.json` without `runner` behaves as before for anyone with Herdr installed. Rollback is reverting the change. An older binary ignores the unknown `runner` key.
