## MODIFIED Requirements

### Requirement: State badge
The state badge SHALL be a circle in the color of the worktree's state with an icon inside that is distinct for each state, so the state can be told without seeing color. The "New" state SHALL be an outlined circle in the neutral status color with its icon in that color, not a filled circle in a brand or primary color. Hovering it SHALL show the state's name, and assistive technologies SHALL read the state's name.

#### Scenario: Needs you
- **WHEN** a worktree's agent is blocked or done
- **THEN** its badge shows the "waiting for you" icon in the warning color, and its tooltip and accessible name say "Waiting for you"

#### Scenario: Unfinished
- **WHEN** a worktree has uncommitted changes and no agent
- **THEN** its badge shows the "unfinished" icon in the neutral color, and its tooltip and accessible name say "Unfinished"

#### Scenario: Same color, different icon
- **WHEN** two states share a similar color
- **THEN** their badges still differ by their icon

#### Scenario: New worktree badge
- **WHEN** a worktree has no commits of its own and isn't merged
- **THEN** its badge is an outlined neutral circle with the "new" icon, and its tooltip and accessible name say "New"

### Requirement: Git cell
The Git cell's first line SHALL show the number of uncommitted changes. Its second line SHALL show that the branch is merged, naming the branch it is merged into in the tooltip; otherwise the commits ahead of ("↑") and behind ("↓") its upstream, with their meaning in the tooltip; otherwise, for a branch with commits of its own and no upstream, a "not pushed" icon whose tooltip and accessible name say the branch isn't pushed. The second line SHALL NOT compare the branch with its base. While the status is loading, the cell SHALL show a loading indicator; when the status can't be read, a muted dash with the reason in its tooltip.

#### Scenario: Changes and divergence
- **WHEN** a worktree has 3 uncommitted changes and is 2 ahead of and 1 behind its upstream
- **THEN** the first line shows 3 changes and the second shows ↑2 ↓1, and hovering it says 2 commits aren't pushed and 1 isn't pulled

#### Scenario: Recorded base
- **WHEN** a worktree's branch has recorded base `epic/redesign`, is 2 ahead of it and even with its upstream
- **THEN** the second line is empty, and the comparison with `epic/redesign` is only in the detail

#### Scenario: Branch not pushed
- **WHEN** a worktree's branch has 2 commits of its own and no upstream
- **THEN** the second line shows the "not pushed" icon, and hovering it says the branch isn't pushed

#### Scenario: New branch without upstream
- **WHEN** a worktree's branch has no commits of its own and no upstream
- **THEN** the second line is empty

#### Scenario: Merged
- **WHEN** a worktree's branch is merged into `develop`
- **THEN** the second line shows it as merged, with `develop` on hover

#### Scenario: Status unavailable
- **WHEN** a worktree's status can't be read
- **THEN** the Git cell shows a muted dash, and hovering it says the status is unavailable

#### Scenario: Merged into the recorded base
- **WHEN** a worktree's branch is merged into its recorded base `epic/redesign` but not into `develop`
- **THEN** the second line shows it as merged, with `epic/redesign` on hover
