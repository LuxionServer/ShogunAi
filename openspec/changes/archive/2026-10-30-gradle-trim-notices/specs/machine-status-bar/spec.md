# Spec Delta

## MODIFIED Requirements

### Requirement: Attention color only when needed
An item SHALL use a status color, always through the theme's status mapping, only when it needs attention: the warning color when its pool is over the limit or DerivedData is over its quota, and the danger color when its last read failed. Otherwise it SHALL use the neutral text colors. A Gradle pool over its limit with no idle daemon needs no attention: its item SHALL use the neutral colors and its tooltip SHALL say it is over the limit because builds are running.

#### Scenario: Pool over the limit
- **WHEN** 9 Gradle daemons are in use and the limit is 3
- **THEN** the Gradle item's icon and figure use the warning color

#### Scenario: Within the limit
- **WHEN** 1 emulator is in use and the limit is 2
- **THEN** the Android emulators item uses the neutral colors

#### Scenario: Gradle pool held by running builds
- **WHEN** 9 Gradle daemons are in use, none of them idle, and the limit is 3
- **THEN** the Gradle item uses the neutral colors and its tooltip says over the limit because builds are running
