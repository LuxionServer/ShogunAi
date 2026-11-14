# build-turn Specification

## Purpose
Bounds how many heavy builds run at the same time on the machine: builds take one of a limited number of build slots shared by every repository and worktree, the others wait in line, and a slot frees itself when its build ends or dies.

## Requirements

### Requirement: Build slots shared by the machine
The machine SHALL have a number of build slots shared by every repository and worktree on any platform. Each slot SHALL be held by one running build, recorded with its worktree, its command, when it started and the process that runs it, outside every working tree, so it never appears in `git status`.

#### Scenario: Held across repositories
- **WHEN** the machine has one build slot, `api` · `TASK-1` runs a build and `main-repo` · `TASK-2` asks for a slot
- **THEN** `TASK-2` doesn't get one while `TASK-1`'s build runs

#### Scenario: Working tree stays clean
- **WHEN** a worktree runs a build through the build turn
- **THEN** `git status` in that worktree shows no new file

### Requirement: Run a build in its turn
`shogun build run [--wait-timeout <minutes>] -- <command> [<arguments>]` SHALL take a build slot for the worktree containing the current directory, run the command in the current directory with the terminal's input, output and environment, give the slot back when the command ends, and exit with the command's exit code.

#### Scenario: Slot free
- **WHEN** a slot is free and an agent in `TASK-1` runs `shogun build run -- ./gradlew test`
- **THEN** `./gradlew test` runs at once with its output on the terminal, and the slot is free again when it ends

#### Scenario: Failing build
- **WHEN** the wrapped command exits with code 3
- **THEN** `shogun build run` exits with code 3 and the slot is free

#### Scenario: Two builds of one worktree
- **WHEN** the machine has two slots and `TASK-1` runs two builds at the same time
- **THEN** each build holds its own slot

### Requirement: Invalid build invocations
`shogun build run` SHALL fail with a usage error and exit code 2 when no command follows `--`, when `--` is missing or when `--wait-timeout` isn't a positive whole number. Outside a Git repository it SHALL say it needs a worktree and exit with code 1 without running the command. When the command can't be started it SHALL say so, give the slot back and exit with code 127.

#### Scenario: No command
- **WHEN** the user runs `shogun build run --`
- **THEN** the command fails with a usage error and exits with code 2

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun build run -- ./gradlew test` outside a Git repository
- **THEN** the command says it needs a worktree, exits with code 1 and `./gradlew` doesn't run

#### Scenario: Unknown executable
- **WHEN** the user runs `shogun build run -- does-not-exist`
- **THEN** the command says it couldn't start `does-not-exist`, exits with code 127 and no slot stays taken

### Requirement: Wait for a build slot
When every slot is taken, `shogun build run` SHALL wait in line and run the command as soon as a slot is free and the worktree is first in line. The wait SHALL be bounded by 30 minutes by default; `--wait-timeout <minutes>` SHALL set the bound. On timeout it SHALL fail with exit code 1, say how long it waited and name the builds holding the slots, without running the command.

#### Scenario: Slot freed while waiting
- **WHEN** the only slot is held by `TASK-1`'s build and `TASK-2` runs `shogun build run -- ./gradlew test`, and `TASK-1`'s build ends
- **THEN** `TASK-2`'s build runs and its exit code is the command's

#### Scenario: Timeout
- **WHEN** the only slot stays held and `TASK-2` runs `shogun build run --wait-timeout 5 -- ./gradlew test`
- **THEN** after 5 minutes the command fails with exit code 1, saying it waited 5 minutes and naming the holder, `./gradlew` doesn't run and `TASK-2` has left the queue

### Requirement: Build waiters are served in order
The builds waiting for a slot SHALL be served first in, first out, across every process and repository on the machine, and a build that isn't first in line SHALL NOT take a slot freed for an earlier waiter. A waiter SHALL leave the queue when it gets a slot, times out, fails or is interrupted, and a waiter whose process no longer runs SHALL be dropped the next time the queue is read.

#### Scenario: Two waiters
- **WHEN** `TASK-2` starts waiting before `TASK-3` and a slot is freed
- **THEN** `TASK-2`'s build runs and `TASK-3` keeps waiting, now first in line

#### Scenario: No jumping the queue
- **WHEN** `TASK-2` waits, a slot is freed and `TASK-4` runs `shogun build run` before `TASK-2` notices
- **THEN** `TASK-4` waits behind `TASK-2` and `TASK-2`'s build runs first

#### Scenario: Interrupted waiter
- **WHEN** `TASK-2` is first in line and its command is interrupted with Ctrl+C
- **THEN** `TASK-2` leaves the queue and the next waiter becomes first

### Requirement: Show progress while waiting for a build slot
While waiting, `shogun build run` SHALL print on standard error a line with its place in line, the number of slots and the worktrees whose builds hold them, and SHALL print a new line only when any of that changes. Nothing SHALL be printed to standard output before the command runs.

#### Scenario: Start of a wait
- **WHEN** `TASK-2` starts waiting while the only slot is held by `api` · `TASK-1`
- **THEN** standard error shows that `TASK-2` is first in line for 1 build slot held by `api` · `TASK-1`

### Requirement: A slot frees itself with its build
A slot SHALL be held only while the process running the build lives. A slot whose process no longer runs, because the build crashed, was killed or the machine restarted, SHALL count as free the next time the slots are read. Slots SHALL NOT expire while their build runs, however long it takes.

#### Scenario: Build killed
- **WHEN** `TASK-1`'s `shogun build run` is killed without a chance to clean up
- **THEN** the next reading of the slots doesn't count it and the first waiter's build runs

#### Scenario: Long build
- **WHEN** `TASK-1`'s build has been running for 45 minutes
- **THEN** it still holds its slot

### Requirement: Build slots from the capacity profile
The number of build slots SHALL follow the machine's capacity profile: Light 1, Balanced 2, One per worktree no limit, Automatic one per 16 GB of physical memory and at most one per 4 cores, between 1 and 4, or 1 when memory can't be read. Custom SHALL use the Automatic value. It SHALL never be read from `.shogunai.json`. Changing the profile SHALL apply to the next build without stopping any.

#### Scenario: 16 GB laptop
- **WHEN** the profile is Automatic and the machine has 16 GB and 8 cores
- **THEN** the machine has 1 build slot

#### Scenario: 64 GB workstation
- **WHEN** the profile is Automatic and the machine has 64 GB and 16 cores
- **THEN** the machine has 4 build slots

#### Scenario: One per worktree
- **WHEN** the profile is One per worktree and five worktrees run `shogun build run`
- **THEN** all five builds run at once

#### Scenario: Lowering the limit
- **WHEN** two builds run and the profile changes from Balanced to Light
- **THEN** both keep running and the next build waits until both have ended

### Requirement: Show the build turn from the command line
`shogun build status` SHALL print the number of slots or that there is no limit, each running build with project, worktree, command and since when, and the waiters in order with project, worktree and since when, and exit with code 0. With `--json` it SHALL print one object: `limit` (a number or `null`), `running` (`[{ repository, worktree, command, since }]`) and `waiting` (`[{ repository, worktree, since }]`), with ISO-8601 times.

#### Scenario: Busy
- **WHEN** the machine has 1 slot, `api` · `TASK-1` runs `./gradlew test` since 14:00 and `main-repo` · `TASK-2` waits since 14:03
- **THEN** the output names `api` · `TASK-1` with `./gradlew test` and 14:00, and `main-repo` · `TASK-2` as waiting since 14:03

#### Scenario: Idle in JSON
- **WHEN** nothing runs, nobody waits and the machine has 2 slots
- **THEN** `shogun build status --json` prints `limit: 2`, `running: []` and `waiting: []`

### Requirement: Builds in the worktree status
`shogun status` SHALL show, for each worktree, how many builds it runs through the build turn and its place in the build queue when it waits, in text and as a `build` field in JSON (`null`, or `{ running, waiting }` where `waiting` is its place in line or `null`).

#### Scenario: Running build
- **WHEN** `TASK-1` runs one build through the build turn and the user runs `shogun status`
- **THEN** `TASK-1`'s line shows that it runs one build

#### Scenario: Waiting build in JSON
- **WHEN** `TASK-2` is second in the build queue and the user runs `shogun status --json`
- **THEN** `TASK-2`'s `build` is `{ "running": 0, "waiting": 2 }`

### Requirement: Heavy builds go through the build turn
Before Claude Code runs a shell command, `shogun hook build-turn` SHALL deny it when it starts a heavy build outside `shogun build run`, and tell the agent to run it again with the build prefixed by `shogun build run --`, in the background or with a long enough shell timeout since it can wait up to 30 minutes. Heavy builds are Gradle (`gradlew`, `gradle`), Maven (`mvn`, `mvnw`), `xcodebuild` and `swift build` or `swift test`.

#### Scenario: Gradle build
- **WHEN** Claude in `TASK-1` is about to run `./gradlew test koverVerify`
- **THEN** the command doesn't run and the agent is told to run `shogun build run -- ./gradlew test koverVerify` instead

#### Scenario: Build in a compound command
- **WHEN** the agent is about to run `cd app && mvn -q verify`
- **THEN** the command doesn't run and the agent is told to prefix `mvn -q verify` with `shogun build run --`

#### Scenario: Already in its turn
- **WHEN** the agent is about to run `shogun build run -- xcodebuild -scheme App test`
- **THEN** the command runs and the hook prints nothing

### Requirement: The build-turn hook leaves other commands alone
`shogun hook build-turn` SHALL print nothing and let the command run for commands that start no heavy build, for light invocations (Gradle `--stop`, `--status`, `--version` or `-v`, and only `help` or `tasks`; Maven `--version` or `-v`; `xcodebuild -version`, `-list`, `-showsdks`, `-showdestinations` and `-showBuildSettings`), for sessions outside a Git repository, when the capacity profile sets no build limit, and when it can't read its input or the settings.

#### Scenario: Light invocation
- **WHEN** the agent runs `./gradlew --stop`
- **THEN** the command runs and the hook prints nothing

#### Scenario: Unrelated command
- **WHEN** the agent runs `grep -r gradle docs`
- **THEN** the command runs and the hook prints nothing

#### Scenario: No limit
- **WHEN** the profile is One per worktree and the agent runs `./gradlew test`
- **THEN** the command runs and the hook prints nothing

#### Scenario: Outside a repository
- **WHEN** a session outside any Git repository runs `./gradlew test`
- **THEN** the command runs and the hook prints nothing
