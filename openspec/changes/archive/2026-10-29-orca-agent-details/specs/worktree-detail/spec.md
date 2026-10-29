## ADDED Requirements

### Requirement: Orca's agent details in the detail
The detail SHALL show, under each agent that carries them (see `worktree-overview`), the tool it waits on with its input, the prompt it was given and its last message, each labelled and shortened to at most three lines. Details that are missing SHALL be omitted.

#### Scenario: Agent waiting on a tool
- **WHEN** the selected worktree's Claude is blocked waiting on `Bash` with input `curl -s https://example.com -o page.html` and prompt `Download the page`
- **THEN** the detail shows Claude as blocked, then that it waits on `Bash: curl -s https://example.com -o page.html`, then the prompt, and no last message

#### Scenario: Long last message
- **WHEN** the agent's last message has ten lines
- **THEN** the detail shows its first three lines and marks it as shortened

### Requirement: Observed ports in the detail
The detail's environment section SHALL show the ports Orca sees the worktree listen on (see `orca-integration`), sorted by number, each with its process and its address when there is one, after the ports ShogunAi reserves. A reserved port that Orca sees listening SHALL be marked as listening. The environment section SHALL appear when there are only observed ports. The observed ports SHALL follow the list's checks without reopening the detail.

#### Scenario: Reserved and observed ports
- **WHEN** the selected worktree has `PORT` reserved as 20001 and Orca sees it listen on 20001 by `java` and on 8765 by `Python` at `http://127.0.0.1:8765`
- **THEN** the environment section shows `PORT 20001` marked as listening, and lists 8765 with `Python` and `http://127.0.0.1:8765` and 20001 with `java` as seen by Orca

#### Scenario: Only observed ports
- **WHEN** the selected worktree has no environment and Orca sees it listen on 8765
- **THEN** the detail shows the environment section with port 8765 seen by Orca

#### Scenario: Orca not running
- **WHEN** Orca isn't running
- **THEN** the environment section shows only the reserved ports, without any listening mark
