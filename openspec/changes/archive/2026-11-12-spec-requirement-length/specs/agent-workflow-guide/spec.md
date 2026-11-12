## MODIFIED Requirements

### Requirement: A catalog of workflow rules by topic
ShogunAi SHALL keep one catalog of workflow rules, grouped into the topics `worktrees`, `parallel`, `resources` and `cleanup`. Every rule SHALL have a fixed text, except that the rules naming a runner name the runner in use, and a condition: always, the session being in the main checkout, the session being in a worktree, the runner in use being available, or the repository declaring a provider.

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
`shogun guide [<topic>]` SHALL print the rules of the topic, or of every topic when none is given, whose condition holds in the current directory: whether it is the main checkout or a worktree, whether the runner in use is available, and which providers the repository declares. A rule whose condition doesn't hold SHALL be printed after the applicable ones, marked with the condition it needs, so an agent can still learn it.

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

### Requirement: Rules for creating and keeping worktrees
The `worktrees` topic SHALL tell agents that every change goes in its own worktree created with `shogun new`, with `--fix`, `--from <ref>`, `--fetch` and `--branch`; that the base is the current worktree's HEAD unless `--from` is given; to check the new worktree's `git log -1`; that the worktree where a session starts never changes branch; to give the person the new worktree's path; and to consult `shogun status` before saying what worktrees exist.

#### Scenario: Branching from an epic
- **WHEN** the user runs `shogun guide worktrees`
- **THEN** the output says to use `shogun new <id> --from <ref>` to branch from something other than the current worktree's HEAD, such as an epic's branch

#### Scenario: Base of a pull request
- **WHEN** the user runs `shogun guide worktrees`
- **THEN** the output says to open a pull request against the branch `shogun base` prints, passing it with `--base`, and what to do when `shogun base` has none recorded

### Requirement: Rules for the desktop turn
The `resources` topic SHALL tell agents, in every repository and on every platform, to take the desktop turn with `shogun desktop acquire --wait` before driving the desktop with computer use, Orca's `orca computer` commands included, running it in the background or with a long enough shell timeout, to acquire again to renew it during long checks before it expires, and to release it with `shogun desktop release` as soon as they are done.

#### Scenario: Guide
- **WHEN** the user runs `shogun guide resources` in any repository
- **THEN** the output says to run `shogun desktop acquire --wait` before computer use and `shogun desktop release` when done

#### Scenario: Session context
- **WHEN** a Claude Code session starts in a worktree
- **THEN** the context doesn't include the rule to take the desktop turn before computer use

#### Scenario: Orca's computer use named
- **WHEN** an agent reads the desktop turn rule from `shogun guide resources`
- **THEN** the rule names `orca computer` as computer use that needs the turn

## ADDED Requirements

### Requirement: The catalog holds no personal preferences
The catalog of workflow rules SHALL NOT contain personal or team preferences such as code style, commits, pull request format or reviewers, tickets or delegation. A rule MAY point to facts ShogunAi records, such as a worktree's base branch.

#### Scenario: Preferences left out
- **WHEN** the catalog's rules are listed
- **THEN** none of them is about code style, commits, pull request format or reviewers, tickets or delegation

#### Scenario: Recorded fact
- **WHEN** a rule needs a worktree's base branch
- **THEN** it points to the base ShogunAi records instead of naming a branch

### Requirement: When the runner in use is available
For the rules whose condition is the runner in use being available, Herdr SHALL count as available when it answers, and Orca when it's installed, since opening in it starts it.

#### Scenario: Herdr not answering
- **WHEN** the runner in use is Herdr, Herdr doesn't answer and the user runs `shogun guide parallel`
- **THEN** the rule to open another session with `shogun open <id> --herdr` is printed after the applicable ones, marked with the condition it needs

#### Scenario: Orca installed but not running
- **WHEN** the runner in use is Orca, Orca is installed but not running, and the user runs `shogun guide parallel`
- **THEN** the rule to open another session with `shogun open <id> --orca` is printed among the applicable ones

### Requirement: Unknown guide topic
`shogun guide <topic>` with a topic that isn't in the catalog SHALL fail with a usage error that lists the topics.

#### Scenario: Unknown topic
- **WHEN** the user runs `shogun guide parties`
- **THEN** a usage error listing the topics is printed on standard error and the command exits with code 2

### Requirement: Rule for the base of a pull request
The `worktrees` topic SHALL tell agents to open a pull request against the branch `shogun base` prints, passing it explicitly, and, when none is recorded, to record it with `shogun base --set <branch>` or ask the person.

#### Scenario: Base recorded
- **WHEN** the user runs `shogun guide worktrees`
- **THEN** the output says to open a pull request against the branch `shogun base` prints, passing it with `--base`

#### Scenario: No base recorded
- **WHEN** the user runs `shogun guide worktrees`
- **THEN** the output says that when `shogun base` has none recorded, the agent records it with `shogun base --set <branch>` or asks the person

### Requirement: The desktop-turn rule is not in the session context
The rule to take the desktop turn SHALL NOT be in the session-start context, since the desktop-turn hook tells the agent what to do when its computer use meets another holder.

#### Scenario: Session context
- **WHEN** a Claude Code session starts in a worktree
- **THEN** the context doesn't include the rule to take the desktop turn before computer use
