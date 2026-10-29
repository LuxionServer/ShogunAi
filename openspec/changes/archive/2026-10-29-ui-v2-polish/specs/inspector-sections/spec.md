## MODIFIED Requirements

### Requirement: Properties as a key and value grid
An inspector section's data SHALL be shown as a property list: one row per property with the key in the interface font and the value in the monospaced data font, keys aligned in one column. Values SHALL be selectable as text. A key or a value that doesn't fit its column SHALL be truncated and show its full text in a tooltip.

#### Scenario: Ports
- **WHEN** a worktree has `PORT` 4123 and `API_PORT` 4124
- **THEN** the Environment section shows two rows, `PORT` with `4123` and `API_PORT` with `4124`, values in the data font

#### Scenario: Long variable
- **WHEN** the Environment section shows `COMPOSE_PROJECT_NAME` with a value wider than its column
- **THEN** the key and the value are truncated, and hovering each shows it whole
