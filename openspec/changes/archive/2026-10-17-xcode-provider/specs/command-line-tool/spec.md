## ADDED Requirements

### Requirement: List DerivedData from the command line
`shogun xcode` SHALL print, inside a repository that declares the Xcode provider on macOS, one line per DerivedData folder of its worktrees with the worktree and size, then the number and size of orphaned folders on the machine, folders on unavailable volumes, and the compilation cache size when caching is declared. Elsewhere it SHALL say the provider isn't active and exit with code 0.

#### Scenario: Worktrees with DerivedData
- **WHEN** the user runs `shogun xcode` in a repository with the provider and two worktrees have DerivedData
- **THEN** each has a line with its worktree and size, followed by the orphans summary

#### Scenario: Provider not declared
- **WHEN** the repository doesn't declare the provider
- **THEN** the command says so and exits with code 0

### Requirement: Delete orphaned DerivedData from the command line
`shogun xcode clean` SHALL delete the orphaned DerivedData folders on the machine following the Xcode provider's deletion rules, print each folder deleted and the space freed, and report folders that couldn't be deleted on standard error with exit code 1.

#### Scenario: Orphans deleted
- **WHEN** the user runs `shogun xcode clean` and two folders are orphaned
- **THEN** both are deleted and the output gives the space freed

#### Scenario: Nothing to delete
- **WHEN** no folder is orphaned
- **THEN** the command says so and exits with code 0

### Requirement: Delete DerivedData when cleaning up from the command line
With `--yes`, `shogun clean --delete-derived-data` SHALL delete the orphaned DerivedData after removing the worktrees, the removed worktrees' folders included, and without `--yes` list the orphans already found. Without the option no DerivedData SHALL be deleted.

#### Scenario: Cleanup with the option
- **WHEN** the user runs `shogun clean --yes --delete-derived-data` and a removed worktree had DerivedData
- **THEN** that folder is deleted and the output says so

#### Scenario: Without the option
- **WHEN** the user runs `shogun clean --yes`
- **THEN** no DerivedData is deleted
