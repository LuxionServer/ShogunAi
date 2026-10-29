## MODIFIED Requirements

### Requirement: A catalog of workflow rules by topic
ShogunAi SHALL keep one catalog of workflow rules, grouped into the topics `worktrees`, `parallel`, `resources` and `cleanup`. Every rule SHALL have a fixed text, except that the rules naming a runner name the runner in use, and a condition: always, the session being in the main checkout, the session being in a worktree, the runner in use being available, or the repository declaring a provider. The catalog SHALL NOT contain personal or team preferences such as code style, commits, pull request format or reviewers, tickets or delegation; a rule MAY point to facts ShogunAi records, such as a worktree's base branch.

#### Scenario: Topics
- **WHEN** the user runs `shogun guide --plain`
- **THEN** the output has the four topics, each with its rules

#### Scenario: No personal preferences
- **WHEN** the catalog's rules are listed
- **THEN** none of them is about commit messages, pull request format, code style or tickets

#### Scenario: Plain guide with Orca in use
- **WHEN** the runner in use is Orca and the user runs `shogun guide --plain`
- **THEN** the parallel rules say `shogun open <id> --orca` and none says `--herdr`

### Requirement: Print the rules that apply
`shogun guide [<topic>]` SHALL print the rules of the topic, or of every topic when none is given, whose condition holds in the current directory: whether it is the main checkout or a worktree, whether the runner in use is available, and which providers the repository declares. Herdr is available when it answers; Orca is available when it's installed, since opening in it starts it. A rule whose condition doesn't hold SHALL be printed after the applicable ones, marked with the condition it needs, so an agent can still learn it. An unknown topic SHALL fail with a usage error that lists the topics.

#### Scenario: In a worktree
- **WHEN** the user runs `shogun guide worktrees` in a linked worktree
- **THEN** the output includes that the worktree's branch must not be changed with `git switch` or `git checkout -b`, and not the main-checkout rule

#### Scenario: In the main checkout
- **WHEN** the user runs `shogun guide worktrees` in the main checkout
- **THEN** the output says to create a worktree with `shogun new` and not to change the checkout's branch

#### Scenario: Provider not declared
- **WHEN** the repository doesn't declare the emulator provider and the user runs `shogun guide resources`
- **THEN** the emulator rules are printed after the others, marked as applying when the repository declares the emulator provider

#### Scenario: Orca in use and installed
- **WHEN** the runner in use is Orca, Orca is installed but not running, and the user runs `shogun guide parallel`
- **THEN** the rule to open another session with `shogun open <id> --orca` is printed among the applicable ones

#### Scenario: Unknown topic
- **WHEN** the user runs `shogun guide parties`
- **THEN** a usage error listing the topics is printed on standard error and the command exits with code 2

### Requirement: Rules for parallel sessions
The `parallel` topic SHALL tell agents to open another session with `shogun open <id> --<runner>`, naming the runner in use (`--herdr` or `--orca`), when that runner is available, or in the path `shogun new` prints otherwise, and to talk to an open session with messages, never by typing into its terminal. The context injected at session start SHALL name the same runner.

#### Scenario: Talking to another session
- **WHEN** Herdr is the runner in use and available, and the user runs `shogun guide parallel`
- **THEN** the output says to open the session with `shogun open <id> --herdr` and to talk to it with messages, not by typing into its terminal

#### Scenario: Session start with Orca in use
- **WHEN** the runner in use is Orca, Orca is installed, and a Claude Code session starts in a worktree
- **THEN** the context says to open another session with `shogun open <id> --orca`
