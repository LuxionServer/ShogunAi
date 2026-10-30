## MODIFIED Requirements

### Requirement: Data text is monospaced with tabular figures
Paths, branch names, ports, process ids, and sizes, counts and durations written as lone figures SHALL be shown in the app's monospaced family with tabular figures, so values that refresh do not shift the layout. Counts inside sentences (such as "5 worktrees · 1.2 GB" or "2 ports · 5 vars") SHALL be shown in the UI family with tabular figures. States written as words (such as "working", "busy" or "idle"), relative dates written as text and project names SHALL be shown in the UI family, not in the monospaced one.

#### Scenario: Size updates in place
- **WHEN** a worktree's size changes from "1.2 GB" to "1.8 GB" during a refresh
- **THEN** the digits keep the same width and the surrounding text doesn't move

#### Scenario: Branch name
- **WHEN** a branch name is shown
- **THEN** it uses the monospaced family

#### Scenario: Agent state
- **WHEN** a row shows "Claude · working" or a Gradle daemon shows "idle · 12 minutes ago"
- **THEN** that text uses the UI family

#### Scenario: Count inside a sentence
- **WHEN** a view's header reads "5 worktrees · 1.2 GB · 2 to clean up"
- **THEN** the sentence uses the UI family with tabular figures, not the monospaced one

#### Scenario: Lone figure
- **WHEN** a row's Git cell shows "✎ 3"
- **THEN** it uses the monospaced family
