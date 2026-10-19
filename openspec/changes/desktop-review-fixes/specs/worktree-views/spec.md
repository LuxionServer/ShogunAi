# Spec Delta

## ADDED Requirements

### Requirement: Errors stay visible in every grouping
A project's load failure and the errors of its actions (removal, cleanup, branch loading, repair, closing Herdr workspaces) SHALL be shown when the view is grouped by state, and when the project's group is collapsed, with the project's name. A project whose load failed SHALL NOT count as having nothing active.

#### Scenario: Failed project in the Active view
- **WHEN** the Active view is grouped by state and one of the shown projects fails to load
- **THEN** the view shows that project's error above the state groups, and doesn't show "Nothing active"

#### Scenario: Collapsed group
- **WHEN** a project group is collapsed and a cleanup in that project fails
- **THEN** the error is still shown under the group header
