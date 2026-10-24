# Spec Delta

## MODIFIED Requirements

### Requirement: Trim DerivedData to the quota
`shogun xcode trim` SHALL apply the DerivedData quota of the machine settings with the same rules as the desktop app, attributing folders to the worktrees of every saved project. It SHALL print each folder released with its size, the space freed and the usage against the quota, and name the worktrees in use when the quota is still exceeded. `--dry-run` SHALL only list what would be released, and `--json` SHALL print the same information as JSON.

#### Scenario: Over the quota
- **WHEN** the quota is 50 GB, usage is 62 GB and an unused worktree folder of 15 GB is the oldest
- **THEN** `shogun xcode trim` prints `released <path>`, `freed 15 GB` and the usage against the quota

#### Scenario: Automatic quota
- **WHEN** the quota is Automatic and resolves to 40 GB
- **THEN** it prints the usage against 40 GB, says the quota is automatic, and `--json` reports the quota mode `automatic` with its size

#### Scenario: Dry run
- **WHEN** the user runs `shogun xcode trim --dry-run` over the quota
- **THEN** it prints `would release <path>` for each candidate and deletes nothing

#### Scenario: Still exceeded
- **WHEN** the remaining folders belong to worktrees in use
- **THEN** it prints that the quota is still exceeded and names those worktrees

### Requirement: Trim without quota or provider
`shogun xcode trim` SHALL say so and delete nothing when the user chose "No quota" or when the Automatic quota can't be computed, and SHALL say the provider isn't active when the repository doesn't declare Xcode or the OS isn't macOS. In every case it SHALL exit with `0`.

#### Scenario: No quota
- **WHEN** the user chose "No quota"
- **THEN** it says that no DerivedData quota is set, deletes nothing and exits with `0`

#### Scenario: Automatic quota unknown
- **WHEN** the quota is Automatic and the free space of the DerivedData volume can't be read
- **THEN** it says the automatic quota couldn't be computed, deletes nothing and exits with `0`

#### Scenario: Provider not active
- **WHEN** the repository doesn't declare the Xcode provider
- **THEN** it says the provider isn't active and exits with `0`
