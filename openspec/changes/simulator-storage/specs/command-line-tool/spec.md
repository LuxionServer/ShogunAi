## ADDED Requirements

### Requirement: Show the simulator storage from the command line
`shogun simulator storage [--json]` SHALL print, on macOS and from any folder, one line per simulator runtime with its version, size, last use and users or the reason it can't be deleted, the total, the unavailable devices with their count and size, and the dyld caches with their size, their stale folders and the command to remove each. Elsewhere it SHALL say it only applies on macOS and exit with code 0.

#### Scenario: Runtimes and caches
- **WHEN** the user runs `shogun simulator storage` with two runtimes and a stale dyld cache
- **THEN** each runtime has its line, the stale folder is marked with its `sudo rm -rf` command, and the command exits with code 0

#### Scenario: JSON
- **WHEN** the user runs `shogun simulator storage --json`
- **THEN** the output gives each runtime's identifier, version, size, last use, users and whether it can be deleted, the unavailable devices and the dyld cache folders

### Requirement: Delete a runtime from the command line
`shogun simulator runtime delete <runtime> [--yes] [--json]` SHALL accept a runtime's version, name or identifier. Without `--yes` it SHALL say what it would delete and its size, and delete nothing. With `--yes` it SHALL delete it following the `simulator-storage` rules, print the space freed and the unavailable devices left, and exit with code 1 when the runtime is refused or the deletion fails.

#### Scenario: Preview
- **WHEN** the user runs `shogun simulator runtime delete 18.5`
- **THEN** the output says iOS 18.5 would be deleted with its size and nothing is deleted

#### Scenario: Deleted
- **WHEN** the user runs `shogun simulator runtime delete 18.5 --yes` and it has no users
- **THEN** it is deleted, the output gives the space freed and suggests `shogun simulator clean-unavailable` when devices were left unavailable

#### Scenario: Refused
- **WHEN** the user runs `shogun simulator runtime delete 26.5 --yes` and a worktree's simulator runs on it
- **THEN** nothing is deleted, standard error names the worktree, and the command exits with code 1

#### Scenario: Ambiguous or unknown runtime
- **WHEN** the given runtime matches none or more than one installed runtime
- **THEN** nothing is deleted, the error lists the installed runtimes, and the command exits with code 1

### Requirement: Delete unavailable devices from the command line
`shogun simulator clean-unavailable [--yes] [--json]` SHALL list the unavailable simulator devices with their count and size and, with `--yes`, delete them and print the space freed.

#### Scenario: Preview
- **WHEN** the user runs `shogun simulator clean-unavailable` with three unavailable devices
- **THEN** they are listed with their size and nothing is deleted

#### Scenario: Deleted
- **WHEN** the user runs `shogun simulator clean-unavailable --yes`
- **THEN** the unavailable devices are deleted and the output gives the space freed

### Requirement: Cleanup leaves the simulator storage alone
`shogun clean` SHALL NOT delete simulator runtimes, unavailable devices nor dyld caches, with any option.

#### Scenario: Cleanup with every option
- **WHEN** the user runs `shogun clean --yes --delete-simulators --delete-derived-data`
- **THEN** no runtime, unavailable device outside the removed worktrees' simulators, nor dyld cache is deleted
