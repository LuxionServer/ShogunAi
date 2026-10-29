# Spec Delta

## ADDED Requirements

### Requirement: Unreachable external tools are shown as an error state
When a view can't be read because an external tool isn't installed or isn't reachable, the view SHALL replace its content with an error state that names the missing tool in plain words, offers "Try again", and keeps the tool's raw message folded under "Technical details" with its copy action.

#### Scenario: Docker not running
- **WHEN** the Compose projects view is shown and Docker's daemon isn't reachable
- **THEN** the view says Docker isn't available, offers "Try again", and shows the socket error only after expanding "Technical details"

#### Scenario: Try again after starting the tool
- **WHEN** the user starts Docker and activates "Try again"
- **THEN** the view is read again and, if it succeeds, the error state is replaced by the project list
