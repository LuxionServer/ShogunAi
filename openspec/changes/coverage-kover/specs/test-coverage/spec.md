## Purpose

Measures how much of ShogunAi's production code the unit tests exercise, per module, and holds each module to a coverage threshold so that untested code can't slip in unnoticed.

## ADDED Requirements

### Requirement: Coverage is measured on every module
The build SHALL measure line and branch coverage of the `core`, `cli` and `desktopApp` modules from their unit tests, and SHALL produce a readable report per module on demand.

#### Scenario: Report after running the tests
- **WHEN** a developer runs the coverage report task of a module
- **THEN** its tests run and a report shows the module's line and branch coverage per class

### Requirement: Coverage counts production code only
Coverage SHALL count only the production code of each module. Test fixtures SHALL be excluded in every module, and in the desktop app composable functions and the code Compose generates for them SHALL be excluded, since the desktop app's end-to-end tests cover them.

#### Scenario: Untested composable
- **WHEN** a composable function of the desktop app is not run by any unit test
- **THEN** the desktop app's coverage is unchanged by it

#### Scenario: Test fixture
- **WHEN** a fake shared through `core`'s test fixtures has code no test runs
- **THEN** `core`'s coverage is unchanged by it

### Requirement: Each module meets its coverage threshold
The build SHALL fail its coverage verification when a module falls below its threshold: 100% of lines in `core` and in `cli`; and, for the branches of `core` and `cli` and for the lines and branches of the desktop app outside its composables, a floor at the whole percentage they had when it was set.

#### Scenario: Untested line in core
- **WHEN** a change adds a line to `core`'s production code that no test runs
- **THEN** the coverage verification fails and names `core` and its line coverage

#### Scenario: Branch coverage below the floor
- **WHEN** a change lowers the branch coverage of a module below its floor
- **THEN** the coverage verification fails

#### Scenario: Coverage at or above every threshold
- **WHEN** every module meets its thresholds
- **THEN** the coverage verification succeeds

### Requirement: Floors only go up
A coverage floor below 100% SHALL only be raised, never lowered, and a change that raises a module's coverage past the next whole percentage SHALL raise that module's floor accordingly, until every threshold reaches 100%.

#### Scenario: Change that adds tests
- **WHEN** a change raises the desktop app's line coverage from its floor to two points above it
- **THEN** the same change raises the desktop app's line floor by those two points
