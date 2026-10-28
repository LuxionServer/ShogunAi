# agent-workflow-guide Specification

## Purpose
Gives agents ShogunAi's workflow rules, versioned with the binary and adapted to where the agent runs, so people don't each keep their own copy of them and agents without hooks learn them too.

## Requirements

### Requirement: A catalog of workflow rules by topic
ShogunAi SHALL keep one catalog of workflow rules, grouped into the topics `worktrees`, `parallel`, `resources` and `cleanup`. Every rule SHALL have a fixed text and a condition: always, the session being in the main checkout, the session being in a worktree, Herdr being available, or the repository declaring a provider. The catalog SHALL NOT contain personal or team preferences such as code style, commits, pull request format or reviewers, tickets or delegation; a rule MAY point to facts ShogunAi records, such as a worktree's base branch.

#### Scenario: Topics
- **WHEN** the user runs `shogun guide --plain`
- **THEN** the output has the four topics, each with its rules

#### Scenario: No personal preferences
- **WHEN** the catalog's rules are listed
- **THEN** none of them is about commit messages, pull request format, code style or tickets

### Requirement: Print the rules that apply
`shogun guide [<topic>]` SHALL print the rules of the topic, or of every topic when none is given, whose condition holds in the current directory: whether it is the main checkout or a worktree, whether Herdr is available, and which providers the repository declares. A rule whose condition doesn't hold SHALL be printed after the applicable ones, marked with the condition it needs, so an agent can still learn it. An unknown topic SHALL fail with a usage error that lists the topics.

#### Scenario: In a worktree
- **WHEN** the user runs `shogun guide worktrees` in a linked worktree
- **THEN** the output includes that the worktree's branch must not be changed with `git switch` or `git checkout -b`, and not the main-checkout rule

#### Scenario: In the main checkout
- **WHEN** the user runs `shogun guide worktrees` in the main checkout
- **THEN** the output says to create a worktree with `shogun new` and not to change the checkout's branch

#### Scenario: Provider not declared
- **WHEN** the repository doesn't declare the emulator provider and the user runs `shogun guide resources`
- **THEN** the emulator rules are printed after the others, marked as applying when the repository declares the emulator provider

#### Scenario: Unknown topic
- **WHEN** the user runs `shogun guide parties`
- **THEN** a usage error listing the topics is printed on standard error and the command exits with code 2

### Requirement: Print every rule without context
`shogun guide --plain` SHALL print every rule of the requested topics with its condition, without looking at the current directory, so it works outside a repository.

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun guide --plain` in a directory that isn't a Git repository
- **THEN** every rule is printed and the command exits with code 0

### Requirement: The guide is a default
Every output of the guide, and the context injected at session start, SHALL state that the rules are ShogunAi's defaults and that the project's `AGENTS.md` and the person's instructions take precedence over them.

#### Scenario: Precedence stated
- **WHEN** the user runs `shogun guide`
- **THEN** the first line says the project's `AGENTS.md` and the person's instructions take precedence

### Requirement: Rules for creating and keeping worktrees
The `worktrees` topic SHALL tell agents that every change goes in its own worktree created with `shogun new`, with `--fix`, `--from <ref>`, `--fetch` and `--branch`; that the base is the current worktree's HEAD unless `--from` is given; to check the new worktree's `git log -1`; that the worktree where a session starts never changes branch; to give the person the new worktree's path; to consult `shogun status` before saying what worktrees exist; and to open a pull request against the branch `shogun base` prints, passing it explicitly, recording it with `shogun base --set <branch>` or asking the person when none is recorded.

#### Scenario: Branching from an epic
- **WHEN** the user runs `shogun guide worktrees`
- **THEN** the output says to use `shogun new <id> --from <ref>` to branch from something other than the current worktree's HEAD, such as an epic's branch

#### Scenario: Base of a pull request
- **WHEN** the user runs `shogun guide worktrees`
- **THEN** the output says to open a pull request against the branch `shogun base` prints, passing it with `--base`, and what to do when `shogun base` has none recorded

### Requirement: Rules for parallel sessions
The `parallel` topic SHALL tell agents to open another session with `shogun open <id> --herdr` when Herdr is available, or in the path `shogun new` prints otherwise, and to talk to an open session with messages, never by typing into its terminal.

#### Scenario: Talking to another session
- **WHEN** Herdr is available and the user runs `shogun guide parallel`
- **THEN** the output says to open the session with `shogun open <id> --herdr` and to talk to it with messages, not by typing into its terminal

### Requirement: Rules for shared resources and cleanup
The `resources` and `cleanup` topics SHALL tell agents that each worktree uses its own emulator and simulator and stops them when done, that a worktree's Compose services are started and built with `shogun compose up` and `shogun compose build` and taken down with `shogun compose down <worktree>`, that `shogun gradle stop` and `shogun xcode clean` are run showing what they free and asking first, and that `shogun clean --yes` is run only after asking.

#### Scenario: Cleaning
- **WHEN** the user runs `shogun guide cleanup`
- **THEN** the output says to ask before running `shogun clean --yes` and names the options that also stop emulators and Gradle daemons and delete simulators, DerivedData and branches

#### Scenario: Compose services
- **WHEN** the repository declares the docker-compose provider and the user runs `shogun guide resources`
- **THEN** the output says to start and build the worktree's services with `shogun compose up` and `shogun compose build`, not with a hand-written project name, and to take them down with `shogun compose down <worktree>`

#### Scenario: Compose rule in the session context
- **WHEN** a Claude Code session starts in a worktree of a repository that declares the docker-compose provider
- **THEN** the context says to start the worktree's services with `shogun compose up`

### Requirement: Install the guide for agents without hooks
`shogun guide install` SHALL write into an instructions file a block delimited by markers that says to run `shogun guide` for ShogunAi's workflow rules and nothing else of the catalog. The file is chosen with `--agent <codex|cursor|gemini>` for that agent's user-level file, `--project` for the repository's `AGENTS.md`, or `--file <path>`. It SHALL create the file when missing, keep every line outside the markers, show what it will write and ask first unless `--yes` is given.

#### Scenario: Gemini's user file
- **WHEN** the user runs `shogun guide install --agent gemini --yes`
- **THEN** Gemini's user-level instructions file contains the block once, and its other content is unchanged

#### Scenario: Project scope
- **WHEN** the user runs `shogun guide install --project --yes` inside a repository
- **THEN** the repository's `AGENTS.md` gets the block and the user-level files are untouched

#### Scenario: Declined
- **WHEN** the user answers no to the confirmation
- **THEN** nothing is written and the command exits with code 0

#### Scenario: No target
- **WHEN** the user runs `shogun guide install` with none of `--agent`, `--project` or `--file`
- **THEN** a usage error listing the options is printed and nothing is written

### Requirement: Installing is idempotent and keeps the block current
Installing again SHALL NOT duplicate the block. A block written by an older version SHALL be replaced, and the text outside the markers SHALL stay byte for byte as it was.

#### Scenario: Installed twice
- **WHEN** the user runs `shogun guide install --agent gemini --yes` twice
- **THEN** the file has one block

#### Scenario: Older block
- **WHEN** the file has a block from an older version
- **THEN** installing replaces its content and leaves the rest of the file unchanged

### Requirement: Uninstall and check the block
`shogun guide uninstall` SHALL remove the block, and the line break it added, from the same targets, leaving everything else. `shogun guide status` SHALL say for each known target whether the block is installed, outdated or missing; `--json` SHALL print the same as a JSON document.

#### Scenario: Uninstall
- **WHEN** a file has the block and the user's own text and the user runs `shogun guide uninstall --file <path> --yes`
- **THEN** only the user's text remains

#### Scenario: Outdated block
- **WHEN** the block in a file comes from an older version
- **THEN** `shogun guide status` reports it as outdated

### Requirement: Warn about duplicated workflow text
When installing, and in `status`, ShogunAi SHALL warn when the target file has `shogun new`, `shogun open` or `shogun clean` outside the block, saying that the file may repeat the guide and suggesting `shogun guide --migrate`. It SHALL NOT change that text.

#### Scenario: Duplicates outside the block
- **WHEN** a file has the user's own paragraph about `shogun new` and the user runs `shogun guide install --file <path> --yes`
- **THEN** the block is added, the paragraph is untouched and a warning mentions `shogun guide --migrate`

### Requirement: Don't inject the same rules twice
`shogun guide status` SHALL warn when Claude Code's `SessionStart` hook is installed and a target file read by Claude Code, or one given with `--file`, also has the block, since both would give the rules to the same session.

#### Scenario: Both present
- **WHEN** the hook is installed and the repository's `AGENTS.md`, which Claude Code reads through `CLAUDE.md`, has the block
- **THEN** the status warns that Claude Code gets the rules twice

### Requirement: Help migrating existing instructions
`shogun guide --migrate [--file <path>]` SHALL print every rule with its id followed by an instruction for an agent: compare the given `AGENTS.md` with the rules, list what it repeats and propose removing those lines, show the diff and apply nothing until the person confirms. It SHALL NOT read or write the file itself.

#### Scenario: Migration prompt
- **WHEN** the user runs `shogun guide --migrate --file ~/AGENTS.md`
- **THEN** the output lists every rule with its id and says to propose and show the removals without applying them, and the file is unchanged

### Requirement: Rules for the desktop turn
The `resources` topic SHALL tell agents, in every repository and on every platform, to take the desktop turn with `shogun desktop acquire --wait` before driving the desktop with computer use, running it in the background or with a long enough shell timeout, to acquire again to renew it during long checks before it expires, and to release it with `shogun desktop release` as soon as they are done.

#### Scenario: Guide
- **WHEN** the user runs `shogun guide resources` in any repository
- **THEN** the output says to run `shogun desktop acquire --wait` before computer use and `shogun desktop release` when done

#### Scenario: Session context
- **WHEN** a Claude Code session starts in a worktree
- **THEN** the context includes the rule to take the desktop turn before computer use
