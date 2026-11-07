# Design

## Context

See proposal.md for the motivation. What already exists and gets reused:

- **Pools of virtual devices**: `ResourcePool.kt` (`PoolHolder`, `HolderProtection`, `ReleaseRecord`), `ReadResourcePoolUseCase` (holders, agent state, release rank) and `AcquireWorktreeResourceUseCase` (acquire, make room, wait). Emulator and simulator reservations live in each worktree's environment state (`EnvironmentStateStore`), and the pool is rebuilt by scanning every worktree.
- **Waiting**: `WaitInLine` and `DeviceWaitQueue` (`NioDeviceWaitQueue` over `~/.shogunai/device-queue.json`), keyed by `WaitQueueName` (`emulator`, `ios-simulator`, `desktop`), first in, first out, with dead processes dropped. They are shared with the desktop turn.
- **A machine-wide exclusive record**: `NioDesktopTurnStore` (`~/.shogunai/desktop-turn.json`), written under the state store's lock.
- **Android tooling**: `LocalAndroidSdk` locates `adb`, and `SharedDeviceStatesAndroidSdk` shares one `adb devices` reading for 2 seconds across projects.
- **Orca**: `OrcaClient.attachEmulator(workspace, serial)` → `orca emulator attach <serial> --worktree … --focus --json`, and `ShowEmulatorInOrcaUseCase` (installed → running → worktree listed → attach, under a 10-second timeout). Orca 1.4.246 streams Android devices with scrcpy over `adb -s <serial>`, and iOS only through serve-sim (simulators).
- **Lease duration** and the agent state that renews leases come from `ResourceSettings.leaseDuration` and Claude Code's hooks.

Checked on the development machine: `xcrun devicectl list devices` reports physical devices with `reality: physical` next to the simulators, including a paired iPhone that belongs to someone else, which is why lending is something the user can take back per device.

## Goals / Non-Goals

**Goals:**

- One model for Android and iOS physical devices that reuses the lease, the release ranking and the queue, without touching how emulators and simulators behave.
- A stable identity across USB and Wi-Fi, so a device keeps its inventory entry, alias and holder when the transport changes.
- No cost for machines and projects that don't use mobile devices.

**Non-Goals:**

- Choosing a physical device automatically in place of a virtual one ("physical if free"). Roadmap.
- Uninstalling the project's app or clearing data when a device is released. Roadmap.
- Pairing devices: `adb pair` for wireless debugging and pairing an iPhone in Xcode stay manual.
- Physical iOS devices on Linux and Windows, Android devices behind a remote `adb` server, and device farms.
- Showing a physical iPhone in Orca, which Orca can't stream today.
- A row indicator for physical devices in the worktree list; the detail, `shogun status` and the Devices view cover it. Roadmap.

## Decisions

### A separate model, not a new `ResourceKind`

`ResourceKind` pools are counted against a capacity profile limit and their holders are found by scanning worktree states. Physical devices have neither: capacity is the inventory, and two worktrees must never be able to claim the same device even when their state files disagree. So physical devices get their own `PhysicalDevice` model and a single machine-wide lease file, `~/.shogunai/device-leases.json` (`PhysicalDeviceLeaseStore`, NIO, written under the same state lock as the queue and the desktop turn), like the desktop turn. Acquiring is one read-modify-write under that lock, so exclusivity holds across processes.

What is shared with the pools is extracted, not duplicated: the release rank (`releaseRank` from agent state and lease) becomes a function used by both, and `WaitInLine` is used as is.

Alternative: storing the device in each worktree's environment state, like the simulator UDID. Rejected because exclusivity would depend on scanning every worktree on every acquisition and on all of them being readable.

### Identity: `ro.serialno` and the UDID

An Android transport serial changes with the transport (`R5CT…` over USB, `192.168.1.20:37xxx` or `adb-R5CT…-xYz._adb-tls-connect._tcp` over Wi-Fi). The inventory key is `android:<ro.serialno>`, read with `adb -s <transport> shell getprop` together with `ro.product.manufacturer`, `ro.product.model`, the marketing name (`ro.product.marketname`, then Samsung's `ro.config.marketing_name`, then the model) and `ro.build.version.release`, in one `adb shell` call. The properties are cached per transport serial for as long as that transport stays listed, so `getprop` runs once per connection. A transport whose `ro.kernel.qemu` is `1` is an emulator reached over TCP and is skipped, as are `emulator-*` serials.

iOS uses `ios:<UDID>` from `devicectl` (`hardwareProperties.udid`, the identifier `xcodebuild -destination id=` takes), which is the same over USB and the network. `devicectl` writes JSON to a file (`--json-output <file>`); the adapter uses a temporary file in the system's temp folder and deletes it.

Which `devicectl` states count as usable (paired, developer mode on, tunnel available) is decided in the adapter from the JSON fields and covered by fixture tests taken from a real device during implementation (task 1.1).

### Inventory and lending

`~/.shogunai/devices.json` (`PhysicalDeviceInventoryStore`) keeps one entry per device: id, platform, alias, name, manufacturer, model, marketing name, OS version, `lent` and `lastSeen`. Every listing updates it (adds new devices as lent, refreshes properties and `lastSeen`), so the inventory grows from `shogun device list`, `acquire`, `status` and the desktop app's Devices view alike. Writing only happens when something changed, to keep the app's refresh from rewriting the file every cycle.

New devices are lent by default, as the user decided: the convenience of plugging in a test phone and having agents use it outweighs the risk, which is mitigated by "Withdraw" being one action away and by the device variables never replacing `ANDROID_SERIAL` (an agent has to name the device explicitly). The risk is stated in the guide page.

Name matching (`--name`) tries the alias exactly, then the device name exactly, ignoring case; `--model` is a case-insensitive substring of the model or the marketing name. A filter is stored with the waiter's ticket.

### Acquiring, taking over and waiting

```
acquire(request) under lock:
  held by me for that platform? ── matches → renew, return
                                └─ doesn't → fail "release first"
  candidates = inventory ∩ request ∩ lent ∩ connected ∩ usable
  free = candidates without a holder
  if others wait and a waiter ahead matches one of them → blocked (not first)
  pick free (least recently acquired first), else
  pick expired, by releaseRank (no session, done/idle, blocked), never working,
       never disconnected
  else → blocked with reasons (held / disconnected / withdrawn / unusable / none match)
```

`--wait` runs that in `WaitInLine.await` with the queues `android-device` and `ios-device` added to `WaitQueueName`. The difference from the pools is that a ticket carries the request (`WaitTicket.request: String?`, the serialized filter), and "first in line" becomes "no earlier ticket matches the device I'm about to take". A request with no platform and no name or model is a usage error only when both platforms have candidates; with `--name` or `--model`, the platform comes from the matching inventory entries. A request that no inventory entry matches fails at once, even with `--wait`, because no connection can make it match without a new device the agent can't know about.

The wait's next wake-up is the earliest of the next lease expiry and a fixed 5-second poll for connections, since a device being plugged in has no expiry to wait for.

### Disconnected devices

A held device that isn't in the latest listing is reported as disconnected and is never given to another worktree, even with an expired lease: taking over a device nobody can reach serves nobody, and keeping it with its holder means plugging it back in resumes that holder's work. Its lease keeps running and the holder's agent working keeps renewing it. Releasing, withdrawing, forgetting and removing the worktree still free it.

### Delivery to the agent

`acquire` prints `export SHOGUN_ANDROID_DEVICE_SERIAL=<transport serial>` or `export SHOGUN_IOS_DEVICE_UDID=<udid>`, and the environment resolution adds them while held, through the same contribution path as the emulator's `ANDROID_SERIAL`, so `shogun env`, the `SessionStart` hook and the environment file all get them. The Android value is the transport serial at the time of resolution: for a Wi-Fi device it can change, which is why the guide tells agents to read it from `acquire` or `shogun device status` after a reconnection.

### Orca

The Orca step reuses `ShowEmulatorInOrcaUseCase`'s checks and `attachEmulator` with the physical device's transport serial. A booted simulator in the worktree skips it (Orca would shut the simulator down). A running emulator doesn't: showing the physical device replaces the emulator's stream in the tab, which task 1.2 checks leaves the emulator running. iOS devices report `unsupported` without calling Orca. When opening a worktree in Orca, a held, connected Android device is shown instead of the running emulator. That precedence is new: the agent asked for the physical device explicitly, so it's the one the user wants to watch.

If task 1.2 shows that Orca doesn't stream a physical device, the requirement stays and every attempt reports `failed` with Orca's message. The roadmap's Orca item then gets the follow-up.

### Desktop app

A `Devices` Machine entry, shown while the inventory isn't empty, with its view and actions, built on the shared list row and the patterns of the Android emulators view; and a section in the worktree detail. Lending, aliases and forgetting live in the Devices view rather than in Preferences, because they act on a list of devices that changes as they are plugged in, which is what Machine views show, while Preferences holds settings. Reads go through `Dispatchers.IO` like the other machine views, and the `adb devices` reading goes through `SharedDeviceStatesAndroidSdk`.

### Coverage

The new `@Serializable` classes (inventory entry, lease record, and the files that hold their lists) give their identity fields no defaults, so they don't add a deserializer branch nobody can reach. If one ends up with every field defaulted, the change raises `core`'s missed-branch maximum by one for it and says so here.

## Risks / Trade-offs

- [A personal phone plugged in to charge is lent by default and an agent installs on it] → Agents only reach it by asking with `shogun device acquire` and passing its serial explicitly; "Withdraw" is one action in the Devices view and the CLI; the guide page says how to withdraw a device and recommends doing it for personal phones.
- [`devicectl`'s JSON is undocumented and changes between Xcode versions] → The adapter reads only a few fields, treats missing ones as "unknown, not usable", and fixture tests pin the format seen on the development machine.
- [`getprop` on a device that has just been plugged in can be slow or fail] → The listing reports that device as connecting and retries on the next listing; it is never acquirable until its identity is known.
- [Polling for connections while waiting runs `adb devices` every 5 seconds] → Only while some command waits for a device that isn't connected, and the reading is shared with other readers within 2 seconds.
- [Orca may not accept a physical serial] → Best effort with `failed`; checked in task 1.2 before building on it.
- [A Wi-Fi transport serial in the environment file goes stale after a reconnection] → `acquire` and `status` always print the current one, and the guide says to use them after a reconnection.

## Migration Plan

Additive: new machine files are created on first use, JSON outputs only gain fields, and nothing changes for repositories or machines without devices. Rolling back leaves `devices.json` and `device-leases.json` unused.
