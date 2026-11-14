# Spec Delta

## MODIFIED Requirements

### Requirement: Limits come from the machine, not the repository
Pool limits SHALL be machine settings stored with the app's settings and SHALL NOT be read from `.shogunai.json`. The desktop app and the `shogun` command SHALL apply the same limits. A repository SHALL only declare which providers it needs. The only exception SHALL be the custom pools a repository declares itself, whose limit is their declared `max`.

#### Scenario: Same limits from the app and the command line
- **WHEN** the machine's emulator limit is 2 and an agent runs `shogun emulator start`
- **THEN** the command applies the limit of 2, as the app would

#### Scenario: Repository can't raise a limit
- **WHEN** `.shogunai.json` contains a field trying to set a pool limit
- **THEN** the limit is not changed

#### Scenario: Custom pool limit
- **WHEN** `.shogunai.json` declares the custom pool `db` with `max` 3
- **THEN** pool `db` has a limit of 3, and the emulator, simulator and Gradle daemon limits are not changed
