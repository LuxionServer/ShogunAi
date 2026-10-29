## ADDED Requirements

### Requirement: Carry the details Orca reports for each agent
Each agent whose state Orca reports SHALL also carry, when Orca reports them: the tool it waits on and that tool's input, only while Orca reports it as waiting; the prompt it was last given; and its last message. Blank values SHALL count as missing. When several agents of the same name in a worktree are reported by Orca, the details SHALL be those of the one whose state is shown. Agents Orca doesn't report SHALL carry no details.

#### Scenario: Waiting on a tool
- **WHEN** Orca reports Claude as waiting, with tool `Bash`, input `curl -s https://example.com -o page.html` and prompt `Download the page`
- **THEN** Claude is blocked, waiting on `Bash` with that input, with that prompt and no last message

#### Scenario: Done after using a tool
- **WHEN** Orca reports Claude as done, with tool `Bash`, prompt `Save the date` and last message `Saved it in now.txt`
- **THEN** Claude is done with that prompt and last message, and isn't waiting on any tool

#### Scenario: Several agents of the same name
- **WHEN** Orca reports two `claude` agents in a worktree, one working with prompt `A` and one waiting on `Bash` with prompt `B`
- **THEN** the worktree's Claude is blocked, waiting on `Bash` with prompt `B`

#### Scenario: Agent Orca doesn't report
- **WHEN** Claude runs in a worktree that Orca doesn't list
- **THEN** Claude carries no tool, prompt or last message
