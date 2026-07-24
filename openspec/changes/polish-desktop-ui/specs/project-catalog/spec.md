## ADDED Requirements

### Requirement: Search and sort the project list
The system SHALL let the user filter the project list by name via a search field, and sort it (e.g. alphabetically), applied client-side over the already-loaded catalog.

#### Scenario: Filter by name
- **WHEN** the user types text into the project list's search field
- **THEN** only projects whose name contains that text (case-insensitive) remain visible

#### Scenario: Clear the search
- **WHEN** the user clears the search field
- **THEN** all persisted projects are shown again

#### Scenario: Sort the list
- **WHEN** the user selects a sort order (e.g. alphabetical)
- **THEN** the displayed project list is reordered accordingly without changing the persisted catalog order
