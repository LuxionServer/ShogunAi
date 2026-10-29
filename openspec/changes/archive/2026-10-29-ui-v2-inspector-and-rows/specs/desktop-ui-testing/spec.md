## ADDED Requirements

### Requirement: Row and inspector components are covered by automated tests
The desktop app SHALL have tests, run by `./gradlew test`, for the shared list row, the inspector section, the property list and the agent indicator, for the mapping of every source's agent state to the shared vocabulary, and for the order of the worktree detail's sections.

#### Scenario: Compact columns
- **WHEN** a UI test renders a row in compact mode with a hidable column and a shrinking column
- **THEN** the hidable column isn't displayed and the shrinking column has its compact width

#### Scenario: Section states
- **WHEN** a UI test renders an inspector section as loading, empty, with content and not applicable
- **THEN** it shows placeholder rows, the empty sentence, the content, and nothing at all, and its label has heading semantics in the first three

#### Scenario: Property copy
- **WHEN** a UI test activates a property's copy action
- **THEN** the value is handed to the copy callback and the action shows "Copied"

#### Scenario: State vocabulary
- **WHEN** a unit test maps blocked, working, done, idle and unknown states from Herdr, Orca and Claude Code hooks
- **THEN** each maps to needs you, working, done, idle and running, and the tooltip names the source

#### Scenario: Inspector order
- **WHEN** a UI test renders the detail of a worktree with status, an agent, ports, an emulator and provisioning
- **THEN** the section headings appear in the order Status, Agent, Environment, Resources, Provisioning
