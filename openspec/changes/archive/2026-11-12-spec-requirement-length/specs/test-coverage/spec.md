## MODIFIED Requirements

### Requirement: Each module meets its coverage threshold
The build SHALL fail its coverage verification when a module falls below its threshold: 100% of lines in `core` and in `cli`; 100% of branches in `cli`; every branch of `core` covered except the deserializer checks kotlinx.serialization generates for `@Serializable` classes whose fields all have defaults, which no input can reach, with the rule set to that count of missed branches.

#### Scenario: Untested line in core
- **WHEN** a change adds a line to `core`'s production code that no test runs
- **THEN** the coverage verification fails and names `core` and its line coverage

#### Scenario: Untested branch in core
- **WHEN** a change adds a condition to `core`'s production code and no test takes one of its outcomes
- **THEN** the coverage verification fails and names `core` and its branch coverage

#### Scenario: Untested branch in cli
- **WHEN** a change adds a condition to `cli`'s production code and no test runs one of its sides
- **THEN** the coverage verification fails and names `cli` and its branch coverage

#### Scenario: Branch coverage below the floor
- **WHEN** a change lowers the branch coverage of a module below its floor
- **THEN** the coverage verification fails

#### Scenario: Coverage at or above every threshold
- **WHEN** every module meets its thresholds
- **THEN** the coverage verification succeeds

## ADDED Requirements

### Requirement: The desktop app meets its coverage floors
The build SHALL fail its coverage verification when the lines or branches of the desktop app outside its composables fall below their floor, set at the whole percentage they had when it was set.

#### Scenario: Desktop branches below the floor
- **WHEN** a change lowers the branch coverage of the desktop app outside its composables below its floor
- **THEN** the coverage verification fails
