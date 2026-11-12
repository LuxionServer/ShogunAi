## ADDED Requirements

### Requirement: Report physical devices in status
`shogun status` SHALL show on each worktree's line the physical devices it holds, with their alias or model and whether they are disconnected, and the platform it waits for with its place in line. `shogun status --json` SHALL add `devices` to every worktree object: `{ "android", "ios" }`, each `null`, `{ "role": "holder", "id", "alias", "model", "connected", "expiresAt" }` or `{ "role": "waiting", "position", "since" }`, with ISO-8601 times.

#### Scenario: Holder and waiter
- **WHEN** `TASK-1` holds the Pixel 8 and `TASK-2` waits for an Android device, first in line
- **THEN** `TASK-1`'s line names the Pixel 8 and `TASK-2`'s line says it waits for an Android device, first in line

#### Scenario: Disconnected
- **WHEN** `TASK-1` holds the A55 and it is unplugged
- **THEN** `TASK-1`'s line says it holds the A55, disconnected

#### Scenario: JSON
- **WHEN** the user runs `shogun status --json` and only `TASK-1` holds a device, the Pixel 8
- **THEN** `TASK-1`'s object has `devices.android.role` `holder` and `devices.ios` `null`, and every other worktree object has both `null`

### Requirement: Physical devices in pool status
`shogun pool status` SHALL list, after the device pools, the lent physical devices per platform with how many are connected and free, and each connected or held device with its alias or model and its holder with lease expiry, or that it is free, followed by the worktrees waiting for each platform in queue order. With `--json` it SHALL add `physicalDevices`: `{ "android", "ios" }`, each with `devices` and `waiting`.

#### Scenario: One held, one free
- **WHEN** the Pixel 8 is held by `main-repo` · `TASK-1` and the A55 is connected and free
- **THEN** `shogun pool status` lists Android physical devices 1 of 2 free, the Pixel 8 held by `main-repo` · `TASK-1` with its lease expiry and the A55 free

#### Scenario: JSON with nobody waiting
- **WHEN** nobody waits for a physical device and the user runs `shogun pool status --json`
- **THEN** `physicalDevices.android.waiting` and `physicalDevices.ios.waiting` are empty arrays
