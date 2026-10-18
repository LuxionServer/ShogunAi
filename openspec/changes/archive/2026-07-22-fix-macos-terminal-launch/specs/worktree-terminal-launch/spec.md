## MODIFIED Requirements

### Requirement: Resolve which terminal emulator to use
The system SHALL resolve a concrete terminal emulator for a launch based on the project's `TerminalPreference.mode`: `FIXED` uses the configured `emulator` directly, `CUSTOM` uses the configured `customCommandTemplate` argv with `{path}`/`{command}` placeholders substituted, and `AUTO_DETECT` picks the first available emulator (via `TerminalEmulatorDetector`) in a fixed, OS-appropriate priority order. On macOS, launching against `Terminal.app` SHALL NOT rely on OS Accessibility/GUI-scripting permissions, so the launch works without any manual permission setup.

#### Scenario: Fixed emulator configured
- **WHEN** `TerminalPreference.mode` is `FIXED` and `emulator` is set
- **THEN** the system builds the launch command for that exact emulator, without running detection

#### Scenario: Custom command template configured
- **WHEN** `TerminalPreference.mode` is `CUSTOM` and `customCommandTemplate` is set
- **THEN** the system substitutes `{path}` and `{command}` into each argv element of the template and launches that argv directly, without invoking a shell to interpret a command string

#### Scenario: Auto-detect finds an available emulator
- **WHEN** `TerminalPreference.mode` is `AUTO_DETECT`
- **THEN** the system queries `TerminalEmulatorDetector` and uses the first available emulator in priority order for the current OS

#### Scenario: Launching Terminal.app on macOS always opens a new window
- **WHEN** the resolved emulator is `MACOS_TERMINAL`
- **THEN** the system opens a new `Terminal.app` window and runs the resolved command in it, regardless of how many `Terminal.app` windows are already open, without requesting or depending on Accessibility permission for GUI scripting
