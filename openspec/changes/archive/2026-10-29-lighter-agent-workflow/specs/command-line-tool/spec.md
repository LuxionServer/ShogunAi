# Spec Delta

## MODIFIED Requirements

### Requirement: Create a worktree from the command line
`shogun new <task-id> [--fix] [--from <ref>]` SHALL create a worktree on a new `feature/` (or `fix/`) branch, and `shogun new --branch <branch>` one from an existing local branch, with the same folder, provisioning, validation and rollback as the desktop app. The new branch SHALL start at `<ref>` when `--from` is given, and otherwise at the HEAD of the worktree containing the current directory. On success it SHALL print the worktree's absolute path alone on standard output and, for a new branch, the commit it starts at, with its short hash and subject, and the `--from` ref when given, on standard error.

#### Scenario: New branch
- **WHEN** the user runs `shogun new TASK-123` in a repository with a project
- **THEN** a worktree on `feature/TASK-123` is created in the project's worktrees folder, provisioned, and its path is the only line on standard output

#### Scenario: Start commit reported
- **WHEN** the user runs `shogun new TASK-123 --from origin/develop` and `origin/develop` points to `a41d265 Merge pull request #12`
- **THEN** standard error says the branch starts from `origin/develop` at `a41d265 Merge pull request #12`, and standard output has only the path

#### Scenario: New branch from a linked worktree
- **WHEN** the user runs `shogun new TASK-123` inside a linked worktree whose HEAD is on `epic/redesign`, while the main checkout is on `develop`
- **THEN** `feature/TASK-123` starts at the commit `epic/redesign` points to, not at `develop`

#### Scenario: Explicit start point
- **WHEN** the user runs `shogun new TASK-123 --from epic/redesign` from the main checkout
- **THEN** `feature/TASK-123` starts at the commit `epic/redesign` points to

#### Scenario: Unknown start point
- **WHEN** the user runs `shogun new TASK-123 --from missing-branch`
- **THEN** the command prints that `missing-branch` doesn't name a commit, creates no branch or folder and exits with code 1

#### Scenario: Start point with an existing branch
- **WHEN** the user runs `shogun new --branch hotfix/login --from develop`
- **THEN** the command prints that `--from` only applies to a new branch and exits with code 2

#### Scenario: Existing branch
- **WHEN** the user runs `shogun new --branch hotfix/login`
- **THEN** a worktree for the existing branch is created and provisioned, its path is printed, and no start commit is reported

#### Scenario: No project and no worktrees folder
- **WHEN** the repository has no project in the desktop app and the user runs `shogun new TASK-123` without `--worktrees-root`
- **THEN** the command explains that a worktrees folder is needed, creates nothing and exits with code 2

#### Scenario: Creation fails
- **WHEN** creation fails, e.g. because the destination folder already exists
- **THEN** the error is printed on standard error and the command exits with code 1

### Requirement: List Gradle daemons from the command line
`shogun gradle` SHALL print one line per Gradle or Kotlin daemon with its process id, kind, Gradle version, memory, state and, inside a Git repository, its worktree, followed by the total memory. Under each busy daemon with a holding build it SHALL print the build: the client's process id and tasks when known, its directory, how long it has been running and the agent session and worktree that started it when known. `shogun gradle --json` SHALL add a `holder` object to each daemon, or `null`. It SHALL work outside a Git repository. Inside a repository that declares the Gradle provider it SHALL also print the build cache notice when it applies.

#### Scenario: Daemons running
- **WHEN** the user runs `shogun gradle` in a repository while an idle and a busy Gradle daemon are running
- **THEN** each one has a line with its state and worktree, and the total memory is printed

#### Scenario: Busy daemon held by another session
- **WHEN** a daemon is busy with `./gradlew benchRun` in the `api` worktree, started two hours ago by the session of the `apps` worktree, and the user runs `shogun gradle`
- **THEN** under the daemon's line a line names the client's process id, `benchRun`, the `api` worktree's directory, about two hours, and the `apps` worktree's session

#### Scenario: Holder in JSON
- **WHEN** the user runs `shogun gradle --json` while a daemon is busy and another is idle
- **THEN** the busy daemon has a `holder` with its directory, start time, client process id, tasks and session when known, and the idle one has `"holder": null`

#### Scenario: No daemons
- **WHEN** no daemon is running
- **THEN** the command says so and exits with code 0

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun gradle` outside any Git repository
- **THEN** the daemons are listed without a worktree column

#### Scenario: Build cache off
- **WHEN** the repository declares the Gradle provider and doesn't enable the build cache
- **THEN** the output ends with the build cache notice

### Requirement: Tell the agent how to check its desktop app
When the repository declares the desktop app provider on macOS, the context printed by `shogun hook session-start` SHALL say in one line to check the app hidden with `shogun headless start` instead of computer use, and to read `shogun guide resources` for how to drive and capture it. `shogun guide resources` SHALL keep the full rule: drive it with the Hot Reload MCP, capture it with `shogun headless capture` instead of the MCP's screenshot, re-read the tree after each view change, hide it again with `shogun headless start` after `restart`, and say what still needs the desktop and that for it the agent takes the desktop turn with `shogun desktop acquire --wait` first.

#### Scenario: Context with the provider
- **WHEN** a Claude Code session starts on macOS in a worktree of a repository that declares the provider
- **THEN** the context has one line that mentions `shogun headless start` and `shogun guide resources`

#### Scenario: Full rule in the guide
- **WHEN** the user runs `shogun guide resources` on macOS in a worktree of a repository that declares the provider
- **THEN** the output mentions `shogun headless capture`, not to use the MCP's `take_screenshot`, hiding again after `restart`, what still needs the desktop and `shogun desktop acquire --wait` for it

#### Scenario: Context on another platform
- **WHEN** a session starts on Linux in a worktree of a repository that declares the provider
- **THEN** the context has no line about `shogun headless`
