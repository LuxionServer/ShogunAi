## ADDED Requirements

### Requirement: Install the build-turn hooks
`shogun hooks install` SHALL give the `PreToolUse` event hooks that run `shogun hook build-turn` for the `Bash` tool, only for commands that mention `gradle`, `mvn`, `xcodebuild` or `swift `, so other shell commands don't start `shogun`.

#### Scenario: Install
- **WHEN** the user runs `shogun hooks install --yes`
- **THEN** the `PreToolUse` event has entries that run `shogun hook build-turn` for the `Bash` tool, with the conditions `Bash(*gradle*)`, `Bash(*mvn*)`, `Bash(*xcodebuild*)` and `Bash(*swift *)`

#### Scenario: Update from the previous version
- **WHEN** the settings have every ShogunAi hook of the previous version and the user runs `shogun hooks status`
- **THEN** the hooks are reported as outdated, and `shogun hooks install --yes` adds the build-turn hooks and keeps the desktop-turn ones
