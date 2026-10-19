# Spec Delta

## ADDED Requirements

### Requirement: Query adb sparingly for emulator states
The desktop app SHALL read the emulator device list with a single `adb devices` call for all the reads made within the same 2 seconds, whichever projects or views ask for it. It SHALL query an emulator's boot property only until it has seen that emulator's boot completed, and again only after that emulator leaves the device list, stops being reported as `device`, or is stopped through ShogunAi.

#### Scenario: Several Android projects checked together
- **WHEN** three projects that declare the emulator provider are checked in the same auto-refresh cycle
- **THEN** `adb devices` runs once for that cycle and all three projects report states from its output

#### Scenario: Booted emulator stays online
- **WHEN** `emulator-5556` has been reported as running and later device lists still show it as `device`
- **THEN** it keeps being reported as running without querying its boot property again

#### Scenario: Emulator restarted
- **WHEN** a running `emulator-5556` disappears from the device list and later shows up again as `device`
- **THEN** its boot property is queried again, and it's reported as booting until the boot completes

#### Scenario: Stopped through ShogunAi
- **WHEN** the user stops `emulator-5556` from ShogunAi and starts it again
- **THEN** its state is read fresh after the stop, and it's reported as booting until its boot property says the boot completed
