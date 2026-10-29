# Design

## Context

`orca-integration` and `orca-multirepos` made Orca a runner: `OrcaClient` (port), `CliOrcaClient` (argv over `orca … --json`), `FakeOrcaClient` (test fixture), `OpenWorktreeInOrcaUseCase` and `OrcaWorktreeReader`. The emulator provider gives each worktree a fixed console port and serial (`emulator-<port>`), starts it with `StartWorktreeEmulatorUseCase` and stops it with `StopWorktreeEmulatorUseCase`, from `shogun emulator start|stop`, the worktree's detail, the machine view's Android emulators, cleanup, removal and pool evictions.

`orca emulator` isn't listed in `orca --help`, but Orca 1.4.243 has it: `list`, `devices`, `attach`, `kill`, `shutdown`, `tap`, `install`, `logcat` and more. This is what it does, checked on this machine with a headless `Tablet_API_35` on port 5580, a ShogunAi worktree open in Orca and an Orca fixture worktree:

- `orca emulator devices --json` lists running Android emulators by serial (`emulator-5580`, `state: booted`, `detail: emulator`) and stopped AVDs by name.
- `orca emulator attach emulator-5580 --worktree path:<abs> --json` takes the running serial, starts nothing and answers `{"attached": true, "info": {"deviceUdid": "emulator-5580", "streamUrl": "scrcpy://emulator-5580", "backend": "android"}}`. Orca's helper is `adb -s emulator-5580 shell … com.genymobile.scrcpy.Server`.
- Without `--focus`, nothing visible happens: opening Orca's emulator tab by hand afterwards still starts its default iPhone simulator, and the AVD has to be picked at the bottom of a long list. With `--focus`, Orca opens a "Mobile Emulator" tab in that worktree with the device selected and live video. Repeating it reuses the same tab. When the worktree isn't the one Orca shows, Orca keeps showing the one it was on.
- Errors: a serial that isn't running gives `emulator_device_not_found`, and a worktree Orca doesn't know gives `selector_not_found`. Both are `ok: false` with exit code 1.
- When the emulator exits, Orca's helper exits too. The tab keeps the last frame and still says "Connected". `orca emulator kill --device <serial>` answers `ok` but doesn't change the tab. `shutdown` runs `xcrun simctl shutdown` and only works on iOS. No command closes the tab. Attaching again after the emulator restarts on the same serial brings the tab back to live video.
- Closing a tab opened from Orca's "+" menu, where the AVD was picked by hand, shut the emulator down. This happened twice. Closing the tab opened by `attach --focus` left it running.

## Goals / Non-Goals

**Goals:**
- A worktree's emulator shows up in its Orca emulator view when ShogunAi starts it, and when ShogunAi opens the worktree in Orca while the emulator runs.
- Never slow down, fail or change an emulator start or an Orca open because of it.

**Non-Goals:**
- iOS simulators. Orca's view shows them as well (attach by UDID), but this change is limited to the emulator step. It goes to the roadmap as a follow-up.
- Detaching, or closing Orca's tab. Orca 1.4.243 has no command for it, and none is needed (see Decisions).
- Watching Orca to attach emulators started by other tools, or reacting to the user's actions in Orca's view.
- Remote Orca servers (`--environment`, `--pairing-code`).

## Decisions

### Attach with `--focus`

Without `--focus` the attach changes nothing the user can see, so the change always passes it. Its side effect is acceptable: it only switches tabs inside the target worktree. In the worktree Orca is showing, the emulator tab comes to the front. In the open flow, the agent's terminal is focused afterwards (see below).

### When to connect

1. **After a successful start**, from `shogun emulator start`, the worktree's detail ("Start") and the machine view's Android emulators. That is when the emulator first exists, and an agent running in Orca usually starts it with `shogun emulator start`. A start that finds the emulator already running attaches too: it's idempotent and refreshes a frozen tab.
2. **When opening a worktree in Orca** (`shogun open --orca`, "Open in Orca", group opens) if the repository declares the emulator provider and the emulator is running. The attach goes after the worktree is made visible and before the agent is focused or started, so the agent's terminal ends up in front.

The start paths ShogunAi uses internally (pool evictions) only stop emulators, so they need nothing.

### When to disconnect: never explicitly

Every way ShogunAi stops an emulator (`emu kill`) ends Orca's helper on its own, which was checked with `ps`. `kill` changes nothing the user can see, and no command closes the tab. Calling Orca on every stop path (stop, cleanup, removal, evictions, the machine view) would add Orca calls and failure handling to code that doesn't need them, and give nothing. The frozen tab is Orca's behavior, and the next start brings it back to live video.

### "Open in Orca" means listed by a running Orca

`ShowEmulatorInOrcaUseCase(orca, samePath)`:

1. Orca not installed → `NotOpen`.
2. `status()` not running → `NotOpen`. Orca is never started for this.
3. `agentsByWorktree()` (one `orca worktree ps`, which lists every visible worktree) has no row whose path is the worktree (`sameDirectory`, which handles `/tmp` and `/private/tmp`) → `NotOpen`.
4. `attachEmulator(OrcaWorkspace.Worktree(path), serial)` → `Shown`.

Any exception, including an Orca too old for `status()`, gives `Failed(message)`. The whole thing runs under a 10-second timeout, so a stuck Orca can't hang an emulator start. The result is `sealed interface OrcaEmulatorView { Shown, NotOpen, Failed(message) }`.

A single `attach` that treats `selector_not_found` as "not open" was ruled out: it would depend on Orca's error codes, and it couldn't tell "not running" apart without the `orca` CLI trying to reach the app.

### Port

`OrcaClient.attachEmulator(workspace: OrcaWorkspace, serial: String)` maps to `orca emulator attach <serial> --worktree <selector> --focus --json`, through the same `run` that parses `ok`/`error`. Its parameter is `OrcaWorkspace`, like the other port methods, but only `Worktree` is used. `FakeOrcaClient` records attached `(workspace key, serial)` pairs and can be given an attach failure.

### Open flow

`OpenWorktreeInOrcaUseCase` gets an optional `runningEmulator: suspend (worktreePath) -> String?`, null by default. The CLI and the app wire it to `ReadEmulatorStatesUseCase`, keeping only `RUNNING`. Because the use case has just made the worktree visible and Orca is running, it calls `orca.attachEmulator` directly in a `runCatching`, without `ShowEmulatorInOrcaUseCase`'s checks. `OrcaOpening` gains `emulator: String?`, the serial that was shown. `OpenGroupInOrcaUseCase` passes the lookup on to each member, using that member's config, so it takes a `(ProjectConfig, String) -> String?` and partially applies it.

### Reporting

- CLI `emulator start`: standard output stays the same (serial, state, `export ANDROID_SERIAL=…`), because agents read it. Standard error gets `shown emulator-5556 in Orca's emulator view` or `shogun: couldn't show the emulator in Orca: <message>`. JSON gets `orcaView: shown|not-open|failed`, in camelCase like the rest of `emulatorJson`, and only for `start`.
- CLI `open --orca`: the text line adds `, with emulator-5556 in its emulator view`. JSON adds `emulator`.
- Desktop app: nothing is shown. The start already reports its own result, and a new notice would add strings in a file five parallel changes are editing, for a mirror. A failure is ignored.

## Risks / Trade-offs

- [Closing a tab the user opened from Orca's menu shuts the emulator down] → That's Orca's behavior, and the tab ShogunAi opens doesn't do it. ShogunAi reads the state from `adb`, so it shows the emulator as stopped, and the next `shogun emulator start` boots it again. Documented in the guide.
- [`--focus` brings the emulator tab to the front of the worktree Orca is showing] → That's the feature: the user or their agent just started it. In the open flow, the agent's terminal is focused last.
- [Two extra Orca calls (`status`, `worktree ps`) per start when Orca is installed] → Both are fast local IPC, small next to an emulator boot, and capped by the timeout.
- [Undocumented command (`orca emulator` isn't in `orca --help`)] → The minimum version stays 1.4.243. A failure only loses the view and is reported as `failed`.

## Migration Plan

None. The change only adds behavior.

## Open Questions

None.
