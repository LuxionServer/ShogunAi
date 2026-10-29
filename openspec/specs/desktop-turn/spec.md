# desktop-turn Specification

## Purpose
Gives agents one turn for driving the machine's desktop (screen, mouse, keyboard and the focused app) with computer use, so only one agent drives it at a time, the others wait in line, and a turn left by an agent that stopped or crashed frees itself.

## Requirements

### Requirement: One desktop turn for the machine
The machine SHALL have a single desktop turn, shared by every repository and worktree on any platform. At most one worktree SHALL hold it at a time. The turn SHALL be recorded with the holder's worktree, when it was acquired and when it expires, outside every working tree, so it never appears in `git status`.

#### Scenario: Held across repositories
- **WHEN** `api` · `TASK-1` holds the desktop turn and `main-repo` · `TASK-2` asks for it
- **THEN** `TASK-2` doesn't get it while `TASK-1` holds it

#### Scenario: Working tree stays clean
- **WHEN** a worktree acquires and releases the desktop turn
- **THEN** `git status` in that worktree shows no new file

### Requirement: Acquire the desktop turn
`shogun desktop acquire [<path>]` SHALL give the desktop turn to the worktree containing the path or the current directory when nobody holds it or the turn has expired, print until when it holds it, and exit with code 0. Outside a Git repository it SHALL say it needs a worktree and exit with code 1.

#### Scenario: Desktop free
- **WHEN** nobody holds the desktop turn and an agent in `TASK-1` runs `shogun desktop acquire`
- **THEN** `TASK-1` holds the turn, the command prints until when, and exits with code 0

#### Scenario: Expired turn taken over
- **WHEN** `TASK-1`'s turn expired a minute ago and `TASK-2` runs `shogun desktop acquire`
- **THEN** `TASK-2` holds the turn and `TASK-1` no longer does

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun desktop acquire` outside a Git repository
- **THEN** the command says it needs a worktree and exits with code 1

### Requirement: Acquiring again renews the turn
When the worktree that holds the desktop turn acquires it again, the turn SHALL be renewed for the full turn duration from that moment, without waiting even when others wait, and the result SHALL say it was renewed.

#### Scenario: Holder renews
- **WHEN** `TASK-1` holds the turn until 14:10, the duration is 10 minutes and it runs `shogun desktop acquire` at 14:05
- **THEN** its turn lasts until 14:15 and the output says it was renewed

#### Scenario: Holder renews while others wait
- **WHEN** `TASK-1` holds the turn, `TASK-2` waits for it and `TASK-1` runs `shogun desktop acquire`
- **THEN** `TASK-1`'s turn is renewed and `TASK-2` keeps waiting

### Requirement: Fail clearly when the desktop is taken
Without `--wait`, acquiring while another worktree holds a valid turn SHALL fail with exit code 1 and an error that names the holder's project and worktree, says when its turn expires, says how many worktrees are waiting when any are, and says that `--wait` waits for the turn. Nothing SHALL change.

#### Scenario: Taken
- **WHEN** `api` · `TASK-1` holds the turn until 14:32 and `TASK-2` runs `shogun desktop acquire`
- **THEN** the command fails with exit code 1, naming `api` · `TASK-1` and 14:32 and pointing to `--wait`

#### Scenario: Others waiting
- **WHEN** the turn is held, two worktrees wait for it and a third runs `shogun desktop acquire` without `--wait`
- **THEN** the command fails and the error says two worktrees are waiting

### Requirement: Wait for the desktop turn
With `--wait`, acquiring while another worktree holds a valid turn SHALL wait instead of failing, and acquire the turn as soon as it is free or expired and the worktree is first in line. The wait SHALL be bounded by 30 minutes by default; `--wait-timeout <minutes>` SHALL set the bound to a positive whole number of minutes and imply `--wait`.

#### Scenario: Turn released while waiting
- **WHEN** `TASK-1` holds the turn and `TASK-2` runs `shogun desktop acquire --wait`, and `TASK-1` then runs `shogun desktop release`
- **THEN** `TASK-2` acquires the turn and the command exits with code 0

#### Scenario: Turn expires while waiting
- **WHEN** `TASK-1`'s turn expires in 3 minutes, nobody renews it and `TASK-2` waits for it
- **THEN** `TASK-2` acquires the turn once `TASK-1`'s expires

#### Scenario: Timeout
- **WHEN** the holder keeps renewing its turn and `TASK-2` runs `shogun desktop acquire --wait-timeout 5`
- **THEN** after 5 minutes the command fails with exit code 1, saying it waited 5 minutes and naming the holder, and `TASK-2` holds nothing and has left the queue

#### Scenario: Invalid timeout
- **WHEN** the user runs `shogun desktop acquire --wait-timeout 0`
- **THEN** the command fails with a usage error and exits with code 2

### Requirement: Waiters for the desktop are served in order
The worktrees waiting for the desktop turn SHALL be served first in, first out, across every process and repository on the machine, and an acquisition by a worktree that isn't first in line SHALL NOT take the turn. A waiter SHALL leave the queue when it acquires, times out, fails or is interrupted, and a waiter whose process no longer runs SHALL be dropped the next time the queue is read.

#### Scenario: Two waiters
- **WHEN** `TASK-2` starts waiting before `TASK-3` and the turn is released
- **THEN** `TASK-2` acquires it and `TASK-3` keeps waiting, now first in line

#### Scenario: Acquire without waiting behind a waiter
- **WHEN** `TASK-2` waits, the turn is released and `TASK-4` runs `shogun desktop acquire` without `--wait` before `TASK-2` takes it
- **THEN** `TASK-4`'s acquisition fails saying one worktree is waiting, and `TASK-2` acquires the turn

#### Scenario: Interrupted waiter
- **WHEN** `TASK-2` is first in line and its command is interrupted with Ctrl+C
- **THEN** `TASK-2` leaves the queue and the next waiter becomes first

#### Scenario: Waiter killed
- **WHEN** the process of the first waiter is killed without a chance to clean up
- **THEN** the next reading of the queue drops it

### Requirement: Show progress while waiting for the desktop
While waiting, the command SHALL print on standard error, in text and JSON mode alike, a line with its place in line, the holder and when its turn expires, and SHALL print a new line only when any of that changes. Nothing SHALL be printed to standard output until the result.

#### Scenario: Start of a wait
- **WHEN** `TASK-2` starts waiting while `api` · `TASK-1` holds the turn until 14:32
- **THEN** standard error shows that `TASK-2` is first in line and that `api` · `TASK-1` holds the desktop until 14:32

#### Scenario: Holder renews
- **WHEN** the holder renews its turn from 14:32 to 14:40 while `TASK-2` waits
- **THEN** `TASK-2` prints a new line with 14:40

### Requirement: The turn expires on its own
A desktop turn SHALL expire after the desktop turn duration from its last acquisition or renewal. Nothing other than the holder acquiring again SHALL renew it, not even the holder's agent working. An expired turn SHALL count as free and SHALL be reported as nobody holding it.

#### Scenario: Agent crashed
- **WHEN** `TASK-1` acquired the turn 11 minutes ago, its agent crashed and the duration is 10 minutes
- **THEN** nobody holds the turn and the next acquisition gets it at once

#### Scenario: Working agent doesn't renew
- **WHEN** `TASK-1`'s agent is recorded as working and its turn reaches its expiry without being acquired again
- **THEN** the turn expires

### Requirement: Desktop turn duration as a machine setting
The machine settings SHALL include the desktop turn duration, a positive whole number of minutes, 10 by default, applied by the desktop app and the `shogun` command alike and never read from `.shogunai.json`. Changing it SHALL apply from the next acquisition or renewal and SHALL NOT end the current turn.

#### Scenario: Default
- **WHEN** the app settings have never stored a desktop turn duration
- **THEN** the duration is 10 minutes

#### Scenario: Repository can't set the duration
- **WHEN** `.shogunai.json` contains a field trying to set the desktop turn duration
- **THEN** the duration is not changed

#### Scenario: Invalid value
- **WHEN** the user types `0`, a negative number or text as the duration
- **THEN** the value is rejected and the stored duration is unchanged

### Requirement: Edit the desktop turn duration in Preferences
Preferences > Resources SHALL offer the desktop turn duration in minutes on every platform, save the change immediately, and have its labels available in English and Spanish.

#### Scenario: Change the duration
- **WHEN** the user enters 15
- **THEN** 15 minutes is stored and shown the next time Preferences opens

### Requirement: Release the desktop turn
`shogun desktop release [<path>]` SHALL release the turn when the worktree containing the path or the current directory holds it, and exit with code 0. When nobody holds the turn it SHALL say so and exit with code 0. When another worktree holds it, it SHALL change nothing, name the holder and exit with code 1.

#### Scenario: Holder releases
- **WHEN** `TASK-1` holds the turn and runs `shogun desktop release`
- **THEN** nobody holds the turn and the command exits with code 0

#### Scenario: Nothing to release
- **WHEN** nobody holds the turn and the user runs `shogun desktop release`
- **THEN** the command says nobody holds the desktop and exits with code 0

#### Scenario: Someone else's turn
- **WHEN** `api` · `TASK-1` holds the turn and `TASK-2` runs `shogun desktop release`
- **THEN** `TASK-1` keeps the turn, the error names `api` · `TASK-1` and the command exits with code 1

### Requirement: Release the turn when the holder's session stops
When `shogun hook agent-state` records `Stop` or `SessionEnd` for a session in the worktree that holds the desktop turn, the turn SHALL be released. The hook SHALL keep printing nothing and never block the session, also when releasing fails. Other events and sessions in other worktrees SHALL NOT affect the turn.

#### Scenario: Response finished
- **WHEN** Claude in `TASK-1` holds the turn and finishes its response, calling the hook with `Stop`
- **THEN** nobody holds the turn and the hook prints nothing

#### Scenario: Session of another worktree
- **WHEN** `TASK-1` holds the turn and a session in `TASK-2` calls the hook with `Stop`
- **THEN** `TASK-1` keeps the turn

#### Scenario: Permission prompt
- **WHEN** Claude in `TASK-1` holds the turn and the hook is called with `Notification`
- **THEN** `TASK-1` keeps the turn

### Requirement: Release the turn with its worktree
Removing a worktree, from the command line, the desktop app or Claude Code's `WorktreeRemove` hook, and cleaning it up SHALL release the desktop turn when that worktree holds it and drop its waits from the queue.

#### Scenario: Holder removed
- **WHEN** `TASK-1` holds the turn and is removed with `shogun remove`
- **THEN** nobody holds the turn and the first waiter acquires it

#### Scenario: Waiter removed
- **WHEN** `TASK-3` waits for the turn and is cleaned up from the desktop app
- **THEN** `TASK-3` is no longer in the queue

### Requirement: Show the desktop turn from the command line
`shogun desktop status` SHALL print who holds the turn, with project, worktree, since when and until when, or that the desktop is free, followed by the waiters in order with project, worktree and since when each waits, and exit with code 0.

#### Scenario: Held with a waiter
- **WHEN** `api` · `TASK-1` holds the turn since 14:00 until 14:10 and `main-repo` · `TASK-2` waits since 14:03
- **THEN** the output names `api` · `TASK-1` with 14:00 and 14:10, and `main-repo` · `TASK-2` as waiting since 14:03

#### Scenario: Free
- **WHEN** nobody holds the turn and nobody waits
- **THEN** the output says the desktop is free

### Requirement: Machine-readable desktop turn
`shogun desktop acquire`, `release` and `status` SHALL accept `--json` and print one object: `holder` (`null`, or `{ repository, worktree, since, expiresAt }`) and `waiting` (`[{ repository, worktree, since }]`); `acquire` SHALL add `renewed` and `waited` (whole seconds waiting, `0` when free, `null` without `--wait`), and `release` SHALL add `released`. Times SHALL be ISO-8601. Errors SHALL leave standard output empty.

#### Scenario: Acquire after waiting
- **WHEN** the user runs `shogun desktop acquire --wait --json` and gets the turn after 40 seconds
- **THEN** standard output is one object whose `holder.worktree` is the current worktree, `renewed` is `false` and `waited` is `40`

#### Scenario: Status when free
- **WHEN** nobody holds the turn and the user runs `shogun desktop status --json`
- **THEN** the object has `holder: null` and `waiting: []`

#### Scenario: Taken in JSON
- **WHEN** another worktree holds the turn and the user runs `shogun desktop acquire --json`
- **THEN** standard output is empty, the error is on standard error and the exit code is 1

### Requirement: Release the turn from the desktop app
The desktop app SHALL let the person release the desktop turn whoever holds it, after a confirmation that names the holder. After the release, the first waiter SHALL be able to acquire it.

#### Scenario: Person frees the desktop
- **WHEN** `TASK-1` holds the turn and the person confirms "Release" in the desktop app
- **THEN** nobody holds the turn and a waiting `TASK-2` acquires it

### Requirement: Orca's computer use takes the desktop turn
Before Claude Code runs a shell command that drives the desktop through Orca's computer use, `shogun hook desktop-turn` SHALL give the desktop turn to the session's worktree, or renew it when that worktree already holds it, and let the command run. A command drives the desktop when it runs `orca computer` (or `orca-ide computer`) with any command other than `capabilities`, `permissions`, `list-apps`, `list-windows` and `get-app-state`, or `get-app-state` with `--restore-window`.

#### Scenario: Desktop free
- **WHEN** nobody holds the desktop turn and Claude in `TASK-1` is about to run `orca computer click --app com.example.app --element-index 4 --json`
- **THEN** `TASK-1` holds the turn and the command runs

#### Scenario: Holder renews
- **WHEN** `TASK-1` holds the turn until 14:10, the duration is 10 minutes and its agent runs `orca computer type-text` at 14:05
- **THEN** its turn lasts until 14:15 and the command runs

#### Scenario: Absolute path in a compound command
- **WHEN** the agent runs `cd /tmp && /opt/homebrew/bin/orca computer hotkey --app com.example.app --key CmdOrCtrl+A`
- **THEN** the hook takes the turn before the command runs

#### Scenario: Window brought to the front
- **WHEN** the agent runs `orca computer get-app-state --app com.example.app --restore-window --json`
- **THEN** the hook takes the turn before the command runs

### Requirement: Orca's computer use waits for another holder
When the desktop turn is held by another worktree, or other worktrees wait for it, `shogun hook desktop-turn` SHALL block the command and tell the agent who holds the turn and until when, how many worktrees wait, and to take its turn with `shogun desktop acquire --wait` run in the background before running the command again. Nothing SHALL change in the turn or the queue.

#### Scenario: Taken
- **WHEN** `api` · `TASK-1` holds the turn until 14:32 and Claude in `TASK-2` is about to run `orca computer click`
- **THEN** the command doesn't run, and the agent is told that `api` · `TASK-1` holds the desktop until 14:32 and to run `shogun desktop acquire --wait` in the background first

#### Scenario: Others waiting
- **WHEN** nobody holds the turn but `TASK-2` waits for it, and Claude in `TASK-3` is about to run `orca computer press-key`
- **THEN** the command doesn't run, the agent is told one worktree is waiting, and `TASK-2` stays first in line

### Requirement: The desktop-turn hook leaves other commands alone
`shogun hook desktop-turn` SHALL print nothing and change nothing for tools other than the shell, for commands that don't drive the desktop through Orca's computer use, and for sessions outside a Git repository. When it can't read the turn or its input, it SHALL let the command run, print nothing and exit with code 0.

#### Scenario: Read-only command
- **WHEN** another worktree holds the turn and the agent runs `orca computer get-app-state --app com.example.app --json`
- **THEN** the command runs and the turn is unchanged

#### Scenario: Unrelated command
- **WHEN** the agent runs `echo computer science`
- **THEN** the hook prints nothing and the turn is unchanged

#### Scenario: Outside a repository
- **WHEN** a session outside any Git repository runs `orca computer click`
- **THEN** the command runs and nobody holds the turn

#### Scenario: Unreadable turn
- **WHEN** the turn's file can't be read and the agent runs `orca computer click`
- **THEN** the command runs and the hook exits with code 0
