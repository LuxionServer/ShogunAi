## MODIFIED Requirements

### Requirement: Each module meets its coverage threshold
The build SHALL fail its coverage verification when a module falls below its threshold: 100% of lines in `core` and in `cli`, and 100% of branches in `cli`; and, for the branches of `core` and for the lines and branches of the desktop app outside its composables, a floor at the whole percentage they had when it was set.

#### Scenario: Untested line in core
- **WHEN** a change adds a line to `core`'s production code that no test runs
- **THEN** the coverage verification fails and names `core` and its line coverage

#### Scenario: Untested branch in cli
- **WHEN** a change adds a condition to `cli`'s production code and no test runs one of its sides
- **THEN** the coverage verification fails and names `cli` and its branch coverage

#### Scenario: Branch coverage below the floor
- **WHEN** a change lowers the branch coverage of a module below its floor
- **THEN** the coverage verification fails

#### Scenario: Coverage at or above every threshold
- **WHEN** every module meets its thresholds
- **THEN** the coverage verification succeeds
