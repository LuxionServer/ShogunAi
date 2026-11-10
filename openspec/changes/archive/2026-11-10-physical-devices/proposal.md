# Proposal

## Why

Some bugs only reproduce on real hardware: a manufacturer's skin (a Samsung bug that needs the A55), the camera, Bluetooth, NFC, push notifications, real performance, or an iPhone's signed build. Today agents can only get emulators and simulators, which ShogunAi creates per worktree, leases and queues; the phones and tablets the user has connected by USB or on the network are invisible to it, so two agents can install on the same phone at once, and an agent can't ask for "the A55 connected a few days ago" because nothing remembers it. A physical device is scarce, exclusive and can't be created, so it fits the pool model already built for virtual devices, with an inventory instead of a capacity profile.

## What Changes

- A machine inventory of physical devices (`~/.shogunai/devices.json`, never `.shogunai.json`): every Android device seen by `adb` (USB or wireless debugging) and every iPhone or iPad seen by `xcrun devicectl` (USB or network, macOS only) is recorded with its stable identity (`ro.serialno` on Android, the UDID on iOS), manufacturer, model, marketing name, OS version and when it was last seen. Emulators and simulators are never recorded.
- A recorded device is **lent to agents by default**. The user can withdraw it (it stays recorded but no agent can take it), lend it again, give it an alias (`a55`, `ipad`) or forget it, from `shogun device` and from the desktop app.
- A new `shogun device` command group: `list [--all]`, `acquire [--android|--ios] [--name <alias-or-name>] [--model <text>] [--wait] [--wait-timeout <minutes>]`, `release [--android|--ios]`, `status`, `lend`, `withdraw`, `rename` and `forget`. A worktree holds at most one Android and one iOS physical device at a time.
- Acquiring follows the virtual devices' discipline: a lease renewed while the worktree's agent works, a first-in, first-out queue per platform across processes with `--wait` and a bounded wait, and taking over the least recently used device whose lease expired. A waiter whose filter doesn't match a freed device doesn't hold back the waiters behind it. Capacity is the set of lent and connected devices, not a limit from the capacity profile.
- Asking for a recorded device that isn't connected fails saying when it was last seen and asking the user to connect it; with `--wait` it waits until it's connected and free, and the wait's progress, `shogun status` and the desktop app say the worktree waits for that device to be connected.
- The device reaches the agent through its own variables, `SHOGUN_ANDROID_DEVICE_SERIAL` and `SHOGUN_IOS_DEVICE_UDID`, printed by `acquire` and delivered with the worktree's environment while held. `ANDROID_SERIAL` and `IOS_SIMULATOR_UDID` keep pointing to the worktree's emulator and simulator, so an `adb` without `-s` never reaches a personal phone by accident. Choosing a physical device over a virtual one is explicit: the agent asks for it.
- Unplugging a held device keeps its holder and lease; it is reported as disconnected, and comes back to the same worktree when reconnected within the lease. Releasing a device never changes anything on it.
- Removing a worktree releases its physical devices and drops its waits.
- `shogun status` shows the physical devices each worktree holds or waits for, and `shogun pool status` lists the physical devices with their holders and waiters, both with additive JSON fields.
- The desktop app gets a "Devices" entry in the Machine section (status bar and overview) with the inventory, each device's connection, holder, lease and waiters, and the actions to release, lend, withdraw, rename and forget; the worktree detail shows the physical devices the worktree holds, with "Release".
- Orca: acquiring an Android physical device, and opening in Orca a worktree that holds a connected one, shows it in the worktree's emulator view in Orca, best effort, unless the worktree's simulator is booted (Orca would shut it down). An iOS physical device is reported as not shown while Orca can't stream one.
- The workflow guide tells agents in repositories with the emulator or iOS simulator provider when to ask for a physical device and how to release it.
- The roadmap gains this item and its follow-ups.

## Capabilities

### New Capabilities

- `physical-devices`: the inventory of physical devices, discovering them through `adb` and `devicectl`, lending and aliases, acquiring, leasing, waiting for and releasing them per worktree, disconnections, delivery to the agent's environment, and the `shogun device` command group.

### Modified Capabilities

- `command-line-tool`: `shogun status` and `shogun pool status` report physical devices.
- `agent-workflow-guide`: the `resources` topic includes the rules for physical devices.
- `machine-resources`: a "Devices" entry in the Machine section with the inventory and its actions.
- `worktree-detail`: the detail shows the worktree's physical devices with "Release".
- `worktree-cleanup`: removing a worktree releases its physical devices.
- `orca-integration`: an acquired Android physical device is shown in Orca's emulator view.

## Impact

- `core`: ports for listing physical devices (`adb devices -l` plus `getprop`, `xcrun devicectl list devices --json-output`), an inventory store, a physical device lease store, use cases to acquire, release, read, lend, withdraw, rename and forget, the wait queue gaining two queues and a per-ticket filter, the environment resolution adding the two variables, worktree removal releasing devices, and the Orca attach reused for a physical serial.
- `cli`: the `shogun device` command group, `status` and `pool status` text and JSON (additive fields), guide text.
- `desktopApp`: the Devices Machine view and status bar item, the worktree detail section, English and Spanish strings.
- Machine state: new `~/.shogunai/devices.json` and `~/.shogunai/device-leases.json`; `device-queue.json` gains the `android-device` and `ios-device` queues. No change to `.shogunai.json`.
- Docs: the providers guide and CLI reference (English and Spanish), the architecture pages on resources, and the roadmap.
