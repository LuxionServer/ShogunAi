# Design

## Context

`orca-emulator-attach` added `OrcaClient.attachEmulator(workspace, serial)` (`orca emulator attach <device> --worktree <selector> --focus --json`), `ShowEmulatorInOrcaUseCase` (installed → running → listed by `worktree ps` → attach, 10-second timeout, `OrcaEmulatorView { Shown, NotOpen, Failed }`), and an optional `runningEmulator` lookup in `OpenWorktreeInOrcaUseCase` and `OpenGroupInOrcaUseCase`. The iOS simulator provider gives each worktree its own simulator (`ShogunAi · <repo> · <worktree>`), boots it with `StartWorktreeSimulatorUseCase` and shuts it down with `StopWorktreeSimulatorUseCase`, from `shogun simulator start|stop`, the worktree's detail and the machine view's iOS simulators.

What Orca 1.4.243 does with simulators, checked on this machine with two disposable simulators (`orca-attach-probe`, iOS 27), a headless `Tablet_API_35` on port 5590, this worktree and a fixture worktree, both open in Orca:

- `orca emulator attach <udid> --worktree path:<abs> --focus --json` answers `{"attached": true, "info": {"deviceUdid": …, "backend": "ios", "helperPid": …}}` in about 4 seconds. Orca's helper is `serve-sim <udid> --exit-on-simulator-shutdown`. Repeating it reuses the helper.
- A UDID that doesn't exist gives `emulator_device_not_found` and a worktree Orca doesn't know gives `selector_not_found`, both `ok: false` with exit code 1, as for Android.
- Attaching a simulator that is shut down boots it.
- `xcrun simctl shutdown` ends Orca's helper within 5 seconds. Booting it again and attaching again starts a new helper.
- **The view holds one device per worktree.** Attaching another device to the same worktree (another simulator, an Android emulator, or even a UDID that doesn't exist, which fails) shuts down the iOS simulator that worktree was showing. An Android emulator that is replaced keeps running. Attaching a device to a different worktree leaves the first worktree's simulator alone.

So the merged Android behavior already shuts down a Kotlin Multiplatform worktree's simulator when `shogun emulator start` runs while Orca shows that simulator.

## Goals / Non-Goals

**Goals:**
- A worktree's simulator shows up in its Orca emulator view when ShogunAi boots it, and when ShogunAi opens the worktree in Orca while it is booted.
- ShogunAi never makes Orca shut a worktree's simulator down.

**Non-Goals:**
- Choosing for the user which of a worktree's two running devices Orca shows (see Decisions).
- Detaching, closing Orca's tab, or reacting to devices the user picks in Orca's view. If the user picks another device in a worktree's view by hand, Orca shuts its simulator down; that's Orca's behavior and is documented.
- Remote Orca servers.

## Decisions

### One use case for both devices

`ShowEmulatorInOrcaUseCase` keeps its name, because Orca calls the view its emulator view, and its flow. Its `invoke(worktreePath, device, otherDeviceRunning)` takes the device (serial or UDID) and a `suspend () -> Boolean` lookup of the worktree's other device. After Orca is known to be running and to list the worktree, a running other device gives `OrcaEmulatorView.Skipped` without attaching. The check goes after the Orca checks so that a worktree not open in Orca reports `not-open`, not a skip that changes nothing. A lookup that throws counts as not running: it reads `adb`/`simctl` state that the start just used, and failing open would only reproduce the old behavior.

`OrcaClient.attachEmulator(workspace, serial)` renames its parameter to `device` and is otherwise the same command; no port method is added.

Rejected: a separate `ShowSimulatorInOrcaUseCase`. It would duplicate the timeout, the error mapping and the Orca checks for a different string.

### Both devices running: show neither

Chosen by the user over "the simulator wins" and "the last one started wins". It's symmetric and never shuts anything down: whichever starts second isn't shown, and the open shows nothing. Showing the first device and keeping it is what Orca already shows, so nothing is lost by not touching it.

- Emulator start: `otherDeviceRunning` is "the worktree's simulator is not shut down" (`ReadSimulatorStatesUseCase.runningUdid`, which treats `BOOTING` as running too).
- Simulator start: `otherDeviceRunning` is "the worktree's emulator is running" (`ReadEmulatorStatesUseCase.runningSerial`).
- Open: `OpenWorktreeInOrcaUseCase` gets `runningSimulator: suspend (String) -> String?` next to `runningEmulator`. Both lookups run; if both return a device it attaches none and sets `devicesSkipped`; otherwise it attaches the one it found, as today. `OpenGroupInOrcaUseCase` passes both on per member.

### Booted only

ShogunAi attaches a simulator only after its start reports it booted, or in the open flow when `runningUdid` finds it not shut down. Since attaching a shut-down simulator boots it, the lookup must never return a shut-down device; the open flow must never boot a device.

### Reporting

- `OrcaEmulatorView` gains `Skipped`; JSON `skipped`.
- CLI `simulator start`: standard output stays as it is. Standard error gets `shown <udid> in Orca's emulator view`, `shogun: couldn't show the simulator in Orca: <message>`, or `shogun: didn't show the simulator in Orca: the worktree's emulator is running too, and Orca shows one device per worktree`. JSON gets `orcaView`, only for `start`.
- CLI `emulator start`: the same skip line, naming the simulator.
- CLI `open --orca`: `OrcaOpening` gains `simulator: String?` and `devicesSkipped: Boolean`. The text line says `, with <udid> in its emulator view` for a simulator, as for an emulator; a skip prints a standard error line. JSON adds `simulator` and `devicesSkipped`.
- Desktop app: nothing new is shown, as for the emulator.

### Timeout raised to 30 seconds

`ShowEmulatorInOrcaUseCase`'s timeout goes from 10 to 30 seconds. Found in the real check: right after a cold boot, `orca emulator attach <udid>` took 12.2 and 12.7 seconds in two runs (about 4 seconds once Orca's helper had run for that simulator before), so the 10-second limit reported `failed` although Orca went on to show it. 30 seconds covers it with margin and still caps a stuck Orca; the open flow's attach has no timeout of its own, as before.

### Wiring

CLI and `AppContainer` wire `runningSimulator` to `ReadSimulatorStatesUseCase.runningUdid` and the other-device lookups from each start path. `AppContainer` already wraps the emulator start (`startEmulatorShownInOrca`); the simulator start gets the same wrapper.

## Risks / Trade-offs

- [The user picks another device in Orca's view by hand and Orca shuts the simulator down] → Orca's behavior; ShogunAi reads the state from `simctl`, shows the simulator as shut down and the next start boots it. Documented in the guide.
- [A worktree with both devices running gets no view from ShogunAi] → The user can pick one in Orca's view; the command line says why nothing was shown.
- [`devicesSkipped` races: the other device stops between the lookup and the attach] → Only loses a view that the next start or open shows.
- [Extra lookups per start (`simctl list` or `adb devices`)] → Run only when Orca is running and lists the worktree; small next to a boot.

## Migration Plan

None. The change adds behavior and only narrows the Android attach in the case where it shut the simulator down.
