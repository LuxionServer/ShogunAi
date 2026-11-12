## ADDED Requirements

### Requirement: Devices entry in the Machine section
While the inventory of physical devices isn't empty, the status bar and the Machine overview SHALL show a "Devices" entry, whose summary says how many devices are connected and how many are held. Its view SHALL list every recorded device with its alias or name, platform, model, OS version, connection or when it was last seen, whether it is lent, and its holder with the lease expiry, then the worktrees waiting for each platform in order.

#### Scenario: Inventory with devices
- **WHEN** the Pixel 8 is connected and held by `api` · `TASK-1` and the A55 is recorded but unplugged
- **THEN** the status bar shows "Devices" with 1 connected and 1 held, and its view lists the Pixel 8 held by `api` · `TASK-1` and the A55 as last seen at its time

#### Scenario: Empty inventory
- **WHEN** no physical device has ever been recorded
- **THEN** neither the status bar nor the Machine overview shows "Devices"

#### Scenario: Go to the holder
- **WHEN** the user activates the holder `api` · `TASK-1` in the Devices view
- **THEN** "All worktrees" opens with `TASK-1` of `api` selected

#### Scenario: Shown while open
- **WHEN** the Devices view is shown and the A55 is plugged in
- **THEN** the view shows it as connected without the user refreshing it

### Requirement: Manage physical devices from the Devices view
The Devices view SHALL offer, for each device, "Release" when held, after a confirmation naming the holder; "Withdraw" or "Lend", where withdrawing a held device asks for confirmation naming the holder; "Rename", which edits its alias with the inventory's rules; and "Forget" for a disconnected device, after a confirmation. Its labels SHALL be available in English and Spanish.

#### Scenario: Withdraw
- **WHEN** the user activates "Withdraw" on the free A55
- **THEN** the A55 is shown as not lent and no agent can acquire it

#### Scenario: Release confirmed
- **WHEN** the user activates "Release" on the Pixel 8 held by `TASK-1` and confirms
- **THEN** `TASK-1` no longer holds it and the view shows it as free

#### Scenario: Forget only when disconnected
- **WHEN** the Pixel 8 is connected
- **THEN** the view offers no "Forget" for it
