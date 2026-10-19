# Spec Delta

## ADDED Requirements

### Requirement: Uninstall the KMP init script from settings
When the KMP init script is installed, project settings SHALL offer to uninstall it, with the same effect as `shogun simulator gradle-init uninstall`.

#### Scenario: Uninstall
- **WHEN** the init script is installed and the user chooses to uninstall it in project settings
- **THEN** the script is removed and the section shows that KMP tests no longer use the worktree's simulator
