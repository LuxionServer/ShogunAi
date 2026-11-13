# Tasks

## 1. Checks on real devices

- [x] 1.1 With a real Android device (USB, then wireless debugging after `adb pair`) and a real paired iPhone or iPad, capture `adb devices -l`, the `getprop` output of the properties listed in design.md and `xcrun devicectl list devices --json-output <file>` (connected by USB, over the network, unplugged, developer mode off if available) as test fixtures with generic placeholders for serials, UDIDs and device names. Verify: the fixtures are under `core/src/test/resources` and contain no real identifier or personal name.
- [x] 1.2 (not run: no Orca session with a device; recorded in design.md as a follow-up) With Orca 1.4.246 or later, a ShogunAi worktree open in Orca and a real Android device, run `orca emulator attach <transport serial> --worktree path:<abs> --focus --json` over USB and Wi-Fi, also while the worktree's emulator runs. Verify: record in design.md whether the device streams, whether the emulator keeps running, and the exact error if it doesn't; adjust the Orca decision only if it changes.

## 2. Discovery and inventory in core

- [x] 2.1 Add the `PhysicalDevice` model (platform, id, transport serial or UDID, connection, usability), the `PhysicalDevices` port and `AdbPhysicalDevices` on top of the shared `adb devices` reading, with properties cached per transport serial and emulators skipped. Verify: tests with the 1.1 fixtures cover every scenario of "Discover connected physical Android devices", including Wi-Fi identity, `unauthorized`, a TCP emulator and no SDK.
- [x] 2.2 Add `DevicectlPhysicalDevices` (macOS only, temporary JSON file deleted afterwards, simulators skipped, missing fields read as not usable). Verify: tests with the 1.1 fixtures cover every scenario of "Discover connected iPhones and iPads", including Linux running nothing.
- [x] 2.3 Add the inventory model, the `PhysicalDeviceInventoryStore` port, `NioPhysicalDeviceInventoryStore` (`~/.shogunai/devices.json`, atomic writes, written only on change) and a fake in the test fixtures, plus `ListPhysicalDevicesUseCase` that merges listings into the inventory (new devices lent, `lastSeen` refreshed). Verify: tests for "Record every physical device seen" and "List physical devices", a missing or corrupt file reading as empty, and no write when nothing changed.
- [x] 2.4 Add the lend, withdraw, rename and forget use cases with typed errors (`DeviceNotFound`, `AmbiguousDevice`, `DuplicateAlias`). Verify: tests for every scenario of "Recorded devices are lent by default", "Name and forget recorded devices" and the ambiguous name of "Manage the inventory from the command line".

## 3. Leases, acquiring and waiting in core

- [x] 3.1 Add the lease record, the `PhysicalDeviceLeaseStore` port, `NioPhysicalDeviceLeaseStore` (`~/.shogunai/device-leases.json`, under the state lock) and a fake, and extract the pools' release rank into a function shared with `ReadResourcePoolUseCase`. Verify: lease store round-trip tests, and every existing pool and `--wait` test passing unchanged.
- [x] 3.2 Add the `android-device` and `ios-device` queues to `WaitQueueName` and an optional request to `WaitTicket`, keeping `device-queue.json` readable by older versions' tickets. Verify: `NioDeviceWaitQueueTest` cases for the new queues next to the existing ones and a ticket without a request.
- [x] 3.3 Implement `AcquirePhysicalDeviceUseCase` per design.md (request matching, one device per platform, renew, take over by release rank, disconnected never taken, typed blocked reasons, `--wait` through `WaitInLine` with the 5-second connection poll and progress, matching-aware queue order). Verify: tests for every scenario of "Acquire a physical device for a worktree", "One physical device per platform per worktree", "Physical device leases", "Take over an expired device when none is free", "Fail clearly when no device is available", "Wait for a physical device", "Waiters for physical devices are served in order" and "Show progress while waiting for a physical device".
- [x] 3.4 Implement `ReleasePhysicalDeviceUseCase` (per platform, nothing held, by worktree for removals, forced for the app, release records for taken and withdrawn devices) and `ReadPhysicalDeviceStatusUseCase` (holder, connection, current serial, lease, place in line, last loss). Verify: tests for "Release a physical device", "Disconnected held devices" and "Report a worktree's physical devices".
- [x] 3.5 Renew a held device's lease while the worktree's agent works, through the same path that renews emulator and simulator leases. Verify: a test that a working agent keeps the device from being taken after the lease duration.
- [x] 3.6 Add `SHOGUN_ANDROID_DEVICE_SERIAL` and `SHOGUN_IOS_DEVICE_UDID` to the environment resolution while held, with declared `env` taking precedence. Verify: tests for every scenario of "Deliver the device to the agent's environment".
- [x] 3.7 Release the devices and drop the waits of a worktree in `WorktreeFolderRemoval`, ignoring failures. Verify: `RemoveWorktreeUseCaseTest` and `CleanUpWorktreesUseCaseTest` cases for a holder and a waiter being removed.
- [x] 3.8 Run `./gradlew :core:test :core:koverVerify`. Verify: it passes at 100% of lines and within the missed-branch maximum; update design.md's Coverage section if a fully defaulted `@Serializable` class raised it.

## 4. Command line

- [x] 4.1 Add `shogun device list|acquire|release|status|lend|withdraw|rename|forget` with their options, `[<path>]` and `--json` to `CliCommand`, `CliHelp` and `ShogunCli`, progress on standard error only on change, and `export` lines in the `acquire` output. Verify: `DeviceCommandsTest` covers exit codes 0/1/2, the text outputs, the JSON objects of "Machine-readable physical device results" and "List physical devices", and the empty standard output on errors.
- [x] 4.2 Add physical devices to `shogun status` and `shogun pool status`, text and JSON. Verify: tests for every scenario of the two `command-line-tool` requirements.
- [x] 4.3 Add the `resources` guide rule for physical devices, only for repositories with the emulator or iOS simulator provider and never in the session context. Verify: `WorkflowRulesTest` covers the three scenarios of "Rules for physical devices" and the session-start line count test still passes.
- [x] 4.4 Add the Orca step after a successful `acquire` (Android: `ShowEmulatorInOrcaUseCase`'s checks and attach with the transport serial, skipped when the simulator is booted; iOS: `unsupported` without calling Orca) and to the Orca open flow (the held, connected Android device in place of the running emulator, `physicalDevice` in the JSON). Verify: tests with `FakeOrcaClient` for every scenario of the three `orca-integration` requirements.
- [x] 4.5 Run `./gradlew :cli:test :cli:e2eTest :cli:koverVerify`. Verify: it passes at 100% of lines and branches.
- [x] 4.6 Document `shogun device` and the new status fields in `docs/guide/cli.md`/`.es.md`, and physical devices (inventory, lending by default and how to withdraw a personal phone, Wi-Fi identity, variables, waiting, Orca) in `docs/guide/providers.md`/`.es.md` and `docs/agents.md`/`.es.md`. Verify: both languages have the same sections and `mkdocs build --strict` passes.

## 5. Desktop app

- [x] 5.1 Add a `PhysicalDevicesState` holder, read on `Dispatchers.IO` only while worktree views or the Devices view are shown and only when the inventory isn't empty or a saved project declares the emulator or iOS simulator provider, and wire the core use cases in `AppContainer`. Verify: holder tests with fakes for following a change made outside the app and for "Read devices without slowing anything down".
- [x] 5.2 Add the "Devices" entry to the status bar and the Machine overview, and its view with the inventory, holders, waiters, go-to-worktree and the Release, Withdraw, Lend, Rename and Forget actions with their confirmations. Verify: UI tests for every scenario of the two `machine-resources` requirements.
- [x] 5.3 Show the worktree's physical devices in its detail with "Release", its waits and its last loss. Verify: UI tests for every scenario of "Physical devices in the detail".
- [x] 5.4 Add every new string in English and Spanish. Verify: the localization completeness test passes.
- [x] 5.5 Run `./gradlew :desktopApp:test :desktopApp:koverVerify`. Verify: it passes, and raise a floor if a figure crossed a whole percentage.
- [x] 5.6 Document the Devices view and the detail section in `docs/guide/desktop-app.md`/`.es.md`, and the physical device model in `docs/architecture/domain.md`/`.es.md` and `docs/architecture/infrastructure.md`/`.es.md`. Verify: `mkdocs build --strict` passes.

## 6. Integration

- [x] 6.1 Run `./gradlew test koverVerify`. Verify: it passes without warnings.
- [x] 6.2 Check end to end with the built CLI, a real Android device and a real iPhone or iPad in two worktrees: acquire by model and by name; the second worktree waits with `--wait` and gets the device on `release` and on lease expiry with a 1-minute lease; unplug and replug (USB to Wi-Fi) while held; ask for a recorded, unplugged device and plug it in while waiting; withdraw a held device; remove a holding worktree. Verify: `shogun device status`, `shogun status`, `shogun pool status` and `shogun env` show each state as specified. (Checked with a real Android phone over USB; Wi-Fi, plugging in while waiting and iOS weren't available and ship pending feedback.)
- [x] 6.3 (checked headless with the inventory and its actions; following outside changes pauses while the window is hidden, as designed, so it rests on the holder tests; the Orca step wasn't run, see 1.2) Check the desktop app with `shogun headless start` from this worktree: the Devices view and the worktree detail follow the CLI-made changes, plugging a device in shows it without refreshing, and each action works. Then check the Orca step with a real Android device. Verify: screenshots of the Devices view and the detail, and Orca's emulator view showing the device (or the `failed` report recorded in 1.2).

## Workflow follow-up

- Archive with `/opsx:archive`, removing the item from `docs/roadmap.md` and `docs/roadmap.es.md` and adding its follow-ups (automatic physical-or-virtual preference, uninstalling the project's app on release, physical devices in the worktree row, showing a physical iPhone in Orca when it can stream one).
- Implementation commit, rebase on `origin/develop`, PR against `develop` assigned to CiroFeliu.
