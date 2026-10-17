## ADDED Requirements

### Requirement: Delete orphaned DerivedData when cleaning up
When the repository declares the Xcode provider, the cleanup dialog SHALL offer an option, checked by default, to also delete orphaned DerivedData, with the number and size of the orphans already found. Orphans SHALL be looked for again after the removals, so the folders of the removed worktrees are included, and a folder that can't be deleted SHALL NOT turn the cleanup into a failure.

#### Scenario: Option shown
- **WHEN** the repository declares the Xcode provider and the user activates "Clean up"
- **THEN** the dialog shows a checked "also delete orphaned Xcode DerivedData" option

#### Scenario: Removed worktree's DerivedData
- **WHEN** the option is checked and a removed worktree had DerivedData
- **THEN** that folder is deleted after the removal and the report includes the space freed

#### Scenario: Cleanup without candidates but with orphans
- **WHEN** no worktree qualifies for cleanup but orphaned DerivedData exists
- **THEN** "Clean up" is enabled and the dialog offers to delete the orphans

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare the Xcode provider
- **THEN** the dialog doesn't show the option and no DerivedData is deleted
