## ADDED Requirements

### Requirement: Tests enforce coverage thresholds
The test workflow SHALL verify every module's coverage threshold after running the tests on its macOS job, the system the thresholds are measured on, and a module below its threshold SHALL fail the check.

#### Scenario: Pull request that lowers coverage
- **WHEN** a pull request to `develop` adds production code to `cli` that no test runs
- **THEN** the macOS job of the check fails, naming the coverage rule of `cli` that was violated

#### Scenario: Pull request that keeps coverage
- **WHEN** a pull request to `develop` passes every test and every module meets its threshold
- **THEN** the check succeeds
