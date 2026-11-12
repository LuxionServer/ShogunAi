## MODIFIED Requirements

### Requirement: The desktop-turn hook leaves other commands alone
`shogun hook desktop-turn` SHALL print nothing and change nothing for tools other than the shell and the computer-use tools that act on the desktop, for shell commands that don't drive the desktop through Orca's computer use, and for sessions outside a Git repository. Computer-use tools that only look SHALL run without the turn. When it can't read the turn or its input, it SHALL let the tool run, print nothing and exit with code 0.

#### Scenario: Read-only command
- **WHEN** another worktree holds the turn and the agent runs `orca computer get-app-state --app com.example.app --json`
- **THEN** the command runs and the turn is unchanged

#### Scenario: Screenshot while the desktop is taken
- **WHEN** another worktree holds the turn and Claude runs `mcp__computer-use__screenshot`
- **THEN** the screenshot runs, the hook prints nothing and the turn is unchanged

#### Scenario: Other tools that only look
- **WHEN** another worktree holds the turn and Claude zooms, reads the cursor position, waits, switches the display, reads the clipboard, lists applications or requests access
- **THEN** the tool runs, the hook prints nothing and the turn is unchanged

#### Scenario: Unrelated command
- **WHEN** the agent runs `echo computer science`
- **THEN** the hook prints nothing and the turn is unchanged

#### Scenario: Outside a repository
- **WHEN** a session outside any Git repository runs `orca computer click`
- **THEN** the command runs and nobody holds the turn

#### Scenario: Unreadable turn
- **WHEN** the turn's file can't be read and the agent runs `orca computer click`
- **THEN** the command runs and the hook exits with code 0
