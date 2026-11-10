## ADDED Requirements

### Requirement: Physical devices in the detail
When the selected worktree holds a physical device, the detail SHALL show it with its alias or model, its connection or that it is disconnected, and its lease expiry, and offer "Release", which releases it after a confirmation naming the device. When it waits for one, the detail SHALL show the platform, its place in line and since when it waits. When it lost a device to another worktree or because the user withdrew it, the detail SHALL say so until it acquires one again.

#### Scenario: Holder selected
- **WHEN** the selected worktree holds the Pixel 8 by USB with a lease until 14:32
- **THEN** the detail shows the Pixel 8, USB, until 14:32, and offers "Release"

#### Scenario: Release confirmed
- **WHEN** the user activates "Release" for the Pixel 8 and confirms
- **THEN** the worktree no longer holds it and the detail no longer shows it

#### Scenario: Taken by another worktree
- **WHEN** the selected worktree's A55 was taken for `TASK-2` at 15:10
- **THEN** the detail says the A55 was taken for `TASK-2` at 15:10
