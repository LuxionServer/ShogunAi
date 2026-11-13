# physical-devices Specification

## Purpose
Lets the worktrees' agents use the physical Android devices, iPhones and iPads connected to the machine, one worktree at a time per device, with an inventory that remembers every device seen, leases, a waiting queue and a release that frees a device left by an agent that stopped.

## Requirements

### Requirement: Discover connected physical Android devices
The system SHALL list the physical Android devices from `adb devices -l`, over USB and wireless debugging, reading each one's `ro.serialno`, manufacturer, model, marketing name and Android version once per connection. Emulators SHALL NOT be listed. A device listed as `unauthorized` or `offline` SHALL be reported with that state and SHALL NOT be acquirable.

#### Scenario: USB device
- **WHEN** `adb devices -l` lists `R5CT10ABCDE device model:SM_A556B`
- **THEN** a physical Android device with identity `R5CT10ABCDE`, manufacturer, model `SM-A556B`, marketing name and Android version is listed as connected by USB

#### Scenario: Wireless debugging
- **WHEN** `adb devices -l` lists `adb-R5CT10ABCDE-xYz._adb-tls-connect._tcp device` whose `ro.serialno` is `R5CT10ABCDE`
- **THEN** it is listed as the same device `R5CT10ABCDE`, connected over the network, with that transport serial

#### Scenario: Emulator ignored
- **WHEN** `adb devices -l` lists `emulator-5556`
- **THEN** it isn't listed as a physical device

#### Scenario: Unauthorized
- **WHEN** a device is listed as `unauthorized`
- **THEN** it is reported as waiting for the user to allow debugging on the device, and it can't be acquired

#### Scenario: No Android SDK
- **WHEN** the machine has no Android SDK
- **THEN** no Android physical device is listed and no error is reported for it

### Requirement: Discover connected iPhones and iPads
On macOS the system SHALL list the physical iOS and iPadOS devices that `xcrun devicectl list devices` reports, over USB or the network, with their UDID, name, model, marketing name and OS version. Simulators SHALL NOT be listed. A device that isn't paired, or has no developer mode, SHALL be reported with that state and SHALL NOT be acquirable. On other systems no iOS device SHALL be listed.

#### Scenario: Paired iPhone
- **WHEN** `devicectl` reports a physical, paired iPhone available over USB
- **THEN** it is listed as connected by USB with its UDID, model and iOS version

#### Scenario: Developer mode off
- **WHEN** a paired iPad reports developer mode disabled
- **THEN** it is listed saying developer mode must be turned on, and it can't be acquired

#### Scenario: Not macOS
- **WHEN** the system runs on Linux
- **THEN** no iOS device is listed and `devicectl` isn't run

### Requirement: Record every physical device seen
Every physical device listed SHALL be added to the machine's inventory, stored with the app's machine state and never in the repository, with its platform, identity, manufacturer, model, marketing name, OS version and when it was last seen, updated each time it is seen. A device SHALL keep its inventory entry when it is disconnected.

#### Scenario: First connection
- **WHEN** a Samsung A55 never seen before is connected by USB and the devices are listed
- **THEN** the inventory has an entry for it with its identity, model, Android version and the current time as last seen

#### Scenario: Disconnected device stays
- **WHEN** a recorded device is unplugged
- **THEN** its entry stays in the inventory with the time it was last seen

#### Scenario: OS updated
- **WHEN** a recorded iPhone is seen again with a newer iOS version
- **THEN** its entry records the new version

### Requirement: Recorded devices are lent by default
A device added to the inventory SHALL be lent to agents. A withdrawn device SHALL stay in the inventory and SHALL NOT be acquired by any worktree. Withdrawing a held device SHALL release it. Lending it again SHALL make it acquirable.

#### Scenario: New device lent
- **WHEN** a device is seen for the first time
- **THEN** it is lent and a worktree can acquire it

#### Scenario: Withdrawn
- **WHEN** the user withdraws the `a55` device and an agent asks for it
- **THEN** the acquisition fails saying the device isn't lent to agents

#### Scenario: Withdraw a held device
- **WHEN** `TASK-1` holds `a55` and the user withdraws it
- **THEN** `TASK-1` no longer holds it and its release is recorded as withdrawn by the user

### Requirement: Name and forget recorded devices
The user SHALL be able to give a recorded device an alias, unique in the inventory, and to forget a device, which removes its entry. Forgetting a held device SHALL release it first. A forgotten device seen again SHALL be added as new.

#### Scenario: Alias
- **WHEN** the user runs `shogun device rename R5CT10ABCDE a55`
- **THEN** the device is listed and can be asked for as `a55`

#### Scenario: Duplicate alias
- **WHEN** another device already has the alias `a55`
- **THEN** the rename fails saying so and nothing changes

#### Scenario: Forget
- **WHEN** the user forgets a disconnected device
- **THEN** its entry is removed and it no longer appears in `shogun device list --all`

### Requirement: List physical devices
`shogun device list` SHALL print the connected physical devices with their alias or name, platform, model, OS version, connection, whether they are lent and the worktree that holds them. With `--all` it SHALL also print the disconnected recorded devices with when they were last seen. It SHALL accept `--json`.

#### Scenario: Connected only
- **WHEN** a Pixel 8 is connected and an A55 is recorded but unplugged
- **THEN** `shogun device list` prints the Pixel 8 only

#### Scenario: All recorded
- **WHEN** the user runs `shogun device list --all`
- **THEN** the A55 is also printed, as disconnected, with when it was last seen

#### Scenario: JSON
- **WHEN** the user runs `shogun device list --all --json`
- **THEN** it prints an array with each device's id, alias, platform, manufacturer, model, marketing name, OS version, connection, lent, last seen and holder

### Requirement: Acquire a physical device for a worktree
`shogun device acquire [<path>]` SHALL give the worktree a lent, connected, acquirable physical device that matches the request and that no other worktree holds, print its platform, alias or name, model and the serial or UDID to use, and exit with code 0. `--android` and `--ios` choose the platform, `--name` matches an alias or device name, and `--model` matches part of the model or marketing name, ignoring case.

#### Scenario: Any Android
- **WHEN** a lent Pixel 8 is connected and free and an agent in `TASK-1` runs `shogun device acquire --android`
- **THEN** `TASK-1` holds the Pixel 8 and the output names it with its serial

#### Scenario: By model
- **WHEN** a lent A55 and a Pixel 8 are connected and free and the agent runs `shogun device acquire --model a55`
- **THEN** the A55 is acquired

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun device acquire` outside a Git repository
- **THEN** the command says it needs a worktree and exits with code 1

#### Scenario: No platform and both available
- **WHEN** an Android and an iOS device are free and the agent runs `shogun device acquire` with no filter
- **THEN** the command fails with a usage error asking for `--android`, `--ios`, `--name` or `--model`, and exits with code 2

### Requirement: One physical device per platform per worktree
A worktree SHALL hold at most one Android and one iOS physical device. Acquiring for a platform whose device the worktree already holds SHALL renew its lease and return it when it matches the request, and SHALL fail without changing anything when it doesn't, saying to release it first.

#### Scenario: Acquire again
- **WHEN** `TASK-1` holds the Pixel 8 and runs `shogun device acquire --android`
- **THEN** it keeps the Pixel 8, its lease is renewed and the output says it was already held

#### Scenario: Another device of the same platform
- **WHEN** `TASK-1` holds the Pixel 8 and runs `shogun device acquire --model a55`
- **THEN** the command fails saying `TASK-1` already holds the Pixel 8 and to release it first, and exits with code 1

#### Scenario: Both platforms
- **WHEN** `TASK-1` holds the Pixel 8 and runs `shogun device acquire --ios`
- **THEN** it also holds a free iPhone

### Requirement: Physical device leases
Holding a physical device SHALL give the worktree a lease of the machine's lease duration. Acquiring it again and every moment the worktree's agent is working SHALL renew it. A device SHALL be taken from its holder for another worktree only when its lease has expired, and never while its holder's agent is working.

#### Scenario: Working agent
- **WHEN** `TASK-1`'s agent is working and the lease would have expired
- **THEN** the lease is renewed and no other worktree can take the device

#### Scenario: Lease expired
- **WHEN** `TASK-1` acquired the Pixel 8 25 minutes ago, its agent has done nothing since and the lease duration is 20 minutes
- **THEN** another worktree asking for an Android device can take the Pixel 8

### Requirement: Take over an expired device when none is free
When no matching device is free, acquiring SHALL take a matching connected device whose lease has expired, choosing first one whose holder has no agent session, then one whose agent is done or idle, then one whose agent is blocked, least recently acquired first within each step. The previous holder SHALL record that it lost the device, when and for which worktree, and the result SHALL say which worktree lost it.

#### Scenario: Take over
- **WHEN** the only Android device is held by `TASK-1` with an expired lease and no agent session, and `TASK-2` runs `shogun device acquire --android`
- **THEN** `TASK-2` holds it, the output says it was taken from `TASK-1`, and `shogun device status` in `TASK-1` says it was taken for `TASK-2` and when

#### Scenario: Blocked agent last
- **WHEN** two expired Android devices are held, one by a worktree whose agent is done and one whose agent is blocked
- **THEN** the one whose agent is done is taken

### Requirement: Fail clearly when no device is available
Without `--wait`, when no matching device can be acquired, acquiring SHALL fail with exit code 1, change nothing, and say why: no recorded device matches; the matching devices are disconnected, naming each one and when it was last seen, and asking the user to connect one; they are withdrawn; they aren't usable yet (unauthorized, unpaired or developer mode off); or they are held, naming each holder with its lease expiry or working agent and how many worktrees wait. It SHALL point to `--wait`.

#### Scenario: Recorded but unplugged
- **WHEN** the agent runs `shogun device acquire --name a55` and the A55 was last seen three days ago
- **THEN** the command fails saying the A55 isn't connected, was last seen three days ago, that the user has to connect it, and that `--wait` waits for it

#### Scenario: Held
- **WHEN** the only Android device is held by `api` · `TASK-1` with a lease until 14:32
- **THEN** the command fails naming `api` · `TASK-1` and 14:32 and pointing to `--wait`

#### Scenario: Nothing matches
- **WHEN** no recorded device matches `--model pixel 9`
- **THEN** the command fails saying no recorded device matches and suggesting `shogun device list --all`

### Requirement: Wait for a physical device
With `--wait`, acquiring SHALL wait instead of failing until a matching device is connected, usable and free or expired, then acquire it. The wait SHALL be bounded by 30 minutes by default, `--wait-timeout <minutes>` SHALL set the bound to a positive whole number of minutes and imply `--wait`, and a timeout SHALL fail with exit code 1, acquire nothing and say why nothing was available. A request that no recorded device can ever match SHALL fail at once.

#### Scenario: Connected while waiting
- **WHEN** the agent runs `shogun device acquire --name a55 --wait` while the A55 is unplugged, and the user connects it
- **THEN** the command acquires the A55 and exits with code 0

#### Scenario: Lease expires while waiting
- **WHEN** the only Android device is held by `TASK-1` with a lease ending in 3 minutes, and `TASK-2` waits for an Android device
- **THEN** `TASK-2` acquires it once the lease expires

#### Scenario: Invalid timeout
- **WHEN** the user runs `shogun device acquire --android --wait-timeout 0`
- **THEN** the command fails with a usage error and exits with code 2

#### Scenario: Unknown device
- **WHEN** the agent runs `shogun device acquire --name pixel9 --wait` and no recorded device is called `pixel9`
- **THEN** the command fails at once saying no recorded device matches

### Requirement: Waiters for physical devices are served in order
The worktrees waiting for a physical device of a platform SHALL be served first in, first out across every process and repository: a device that becomes available SHALL go to the longest-waiting worktree whose request it matches, a waiter it doesn't match SHALL NOT hold back the ones behind, and an acquisition without `--wait` SHALL NOT take a device a waiter matches. Waiters leave the queue as for the device pools.

#### Scenario: Filter doesn't match
- **WHEN** `TASK-2` waits for `--name a55`, `TASK-3` waits for any Android device after it, and a Pixel 8 becomes free
- **THEN** `TASK-3` acquires the Pixel 8 and `TASK-2` keeps waiting, first in line

#### Scenario: Interrupted waiter
- **WHEN** `TASK-2` waits for an Android device and its command is interrupted with Ctrl+C
- **THEN** `TASK-2` leaves the queue

#### Scenario: Waiter killed
- **WHEN** the process of the first waiter for an iOS device is killed without a chance to clean up
- **THEN** the next reading of the queue drops it

#### Scenario: No jumping the queue
- **WHEN** `TASK-2` waits for any Android device, the Pixel 8 becomes free and `TASK-4` runs `shogun device acquire --android` without `--wait` before `TASK-2` takes it
- **THEN** `TASK-4`'s acquisition fails saying one worktree is waiting, and `TASK-2` acquires the Pixel 8

### Requirement: Show progress while waiting for a physical device
While waiting, the command SHALL print on standard error, in text and JSON mode alike, its place in line and why it waits: the matching devices that are held, with their holders and lease expiries, and those that aren't connected, asking the user to connect one. It SHALL print a new line only when any of that changes, and nothing on standard output until the result.

#### Scenario: Waiting for a connection
- **WHEN** `TASK-2` waits for `--name a55` while it's unplugged
- **THEN** standard error says `TASK-2` is first in line and waits for the A55 to be connected

### Requirement: Release a physical device
`shogun device release [<path>]` SHALL release the physical devices the worktree holds, or only one platform's with `--android` or `--ios`, and say what it released. Releasing SHALL NOT run anything on the device. Releasing when the worktree holds nothing SHALL succeed and say so.

#### Scenario: Release
- **WHEN** `TASK-1` holds the Pixel 8 and runs `shogun device release`
- **THEN** it no longer holds it, nothing is installed, removed or changed on the device, and the next waiter can acquire it

#### Scenario: Nothing held
- **WHEN** `TASK-1` holds no physical device and runs `shogun device release`
- **THEN** the command says it held none and exits with code 0

### Requirement: Disconnected held devices
A held device that gets disconnected SHALL stay held with its lease, and SHALL be reported as disconnected to its holder and in every view. Reconnected within the lease, it SHALL be the holder's again with its current serial. While disconnected it SHALL NOT be given to any other worktree, even after its lease expires.

#### Scenario: Unplugged while held
- **WHEN** `TASK-1` holds the Pixel 8 and it is unplugged
- **THEN** `shogun device status` in `TASK-1` reports the Pixel 8 as held and disconnected, with its lease expiry

#### Scenario: Back over Wi-Fi
- **WHEN** the Pixel 8 held by `TASK-1` is reconnected over wireless debugging with a new transport serial
- **THEN** `TASK-1` still holds it and `shogun device status` prints the new serial

### Requirement: Report a worktree's physical devices
`shogun device status [<path>]` SHALL print, for each platform, the device the worktree holds with its connection, serial or UDID and lease expiry, or that it holds none, why it lost the last one when it was taken or withdrawn, and its place in line when it waits. It SHALL accept `--json`.

#### Scenario: Holds one
- **WHEN** `TASK-1` holds the Pixel 8 by USB with a lease until 14:32
- **THEN** the output names the Pixel 8, its serial, USB and 14:32, and says it holds no iOS device

### Requirement: Deliver the device to the agent's environment
While a worktree holds an Android physical device, its environment SHALL contain `SHOGUN_ANDROID_DEVICE_SERIAL` with that device's serial, and while it holds an iOS one, `SHOGUN_IOS_DEVICE_UDID` with its UDID, delivered by `shogun env`, the `SessionStart` hook and the environment file, and printed by `acquire` as lines to export. `ANDROID_SERIAL` and `IOS_SIMULATOR_UDID` SHALL NOT change. A variable of the same name declared in `env` SHALL take precedence.

#### Scenario: Acquire prints the variable
- **WHEN** `TASK-1` acquires the Pixel 8 with serial `R5CT10ABCDE`
- **THEN** the output includes `export SHOGUN_ANDROID_DEVICE_SERIAL=R5CT10ABCDE` and `shogun env` includes it

#### Scenario: Emulator unchanged
- **WHEN** `TASK-1` holds emulator `emulator-5556` and acquires the Pixel 8
- **THEN** its environment keeps `ANDROID_SERIAL=emulator-5556`

#### Scenario: Released
- **WHEN** `TASK-1` releases the Pixel 8
- **THEN** its environment no longer contains `SHOGUN_ANDROID_DEVICE_SERIAL`

### Requirement: Machine-readable physical device results
With `--json`, `acquire`, `release` and `status` SHALL print one object per result with, per platform, the device's id, alias, model, connection, serial or UDID and lease expiry, and `acquire` SHALL include `waited` (whole seconds spent waiting, `0` when nothing was waited, `null` without `--wait`) and `takenFrom` (the worktree that lost the device, or `null`).

#### Scenario: Acquire JSON
- **WHEN** the user runs `shogun device acquire --android --wait --json` and acquires the Pixel 8 after 40 seconds
- **THEN** standard output is one line with an object whose `android.serial` is the Pixel 8's serial and `waited` is `40`

### Requirement: Manage the inventory from the command line
`shogun device lend <device>`, `withdraw <device>`, `rename <device> <alias>` and `forget <device>` SHALL accept a device's id, alias or name, change the inventory, print the result and accept `--json`. A device that matches no entry, or several, SHALL fail with exit code 1 naming the candidates.

#### Scenario: Withdraw by alias
- **WHEN** the user runs `shogun device withdraw a55`
- **THEN** the A55 is withdrawn and the output says no agent can acquire it until it's lent again

#### Scenario: Ambiguous name
- **WHEN** two recorded iPhones are named `iPhone` and the user runs `shogun device forget iPhone`
- **THEN** the command fails listing both with their ids and exits with code 1

### Requirement: Read devices without slowing anything down
The desktop app and the command line SHALL read connected devices only when something needs them, read the device properties of a connection once, and share one `adb devices` reading with the emulator states read within the same 2 seconds. A machine whose inventory is empty and whose saved projects declare neither the emulator nor the iOS simulator provider SHALL NOT run `adb` or `devicectl` for physical devices from the desktop app.

#### Scenario: Nothing Android or iOS
- **WHEN** the inventory is empty and no saved project declares the emulator or iOS simulator provider
- **THEN** the desktop app runs neither `adb` nor `devicectl` to look for physical devices
