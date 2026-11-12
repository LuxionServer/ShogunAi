## ADDED Requirements

### Requirement: Rules for physical devices
In a repository that declares the emulator or the iOS simulator provider, the `resources` topic SHALL tell agents to ask for a physical device with `shogun device acquire` only when real hardware or a specific model is needed, to find recorded ones with `shogun device list --all`, to wait with `--wait` in the background, to pass `SHOGUN_ANDROID_DEVICE_SERIAL` or `SHOGUN_IOS_DEVICE_UDID` explicitly, and to run `shogun device release` when done. It SHALL NOT be in the session context.

#### Scenario: Android repository
- **WHEN** the repository declares the emulator provider and the user runs `shogun guide resources`
- **THEN** the output says to use `shogun device acquire` for real hardware, to pass `-s "$SHOGUN_ANDROID_DEVICE_SERIAL"` to `adb`, and to run `shogun device release` when done

#### Scenario: Backend repository
- **WHEN** the repository declares only the docker-compose provider and the user runs `shogun guide resources`
- **THEN** the output has no rule about physical devices

#### Scenario: Session context
- **WHEN** a Claude Code session starts in a worktree of a repository with the emulator provider
- **THEN** the context has no rule about physical devices
