# device-capacity-wait Specification

## Purpose
Lets a worktree wait for an Android emulator or an iOS simulator when its pool is full, instead of failing, with the waiters of a pool served in the order they asked and every wait bounded in time.

## Requirements

### Requirement: Wait for capacity on start
`shogun emulator start` and `shogun simulator start` SHALL accept `--wait`. With it, when the worktree holds no device, the pool is at its limit and none of its devices is releasable, the command SHALL wait instead of failing, retrying the acquisition until it succeeds, then start the device as a start without `--wait` does. Without `--wait` a full pool SHALL fail as before.

#### Scenario: Capacity frees up
- **WHEN** the emulator limit is 1, `TASK-1`'s emulator has a lease valid for 3 more minutes and `TASK-2` runs `shogun emulator start --wait`
- **THEN** the command waits, and once `TASK-1`'s lease expires it releases `TASK-1`'s emulator, starts `TASK-2`'s and exits with code 0

#### Scenario: Room available
- **WHEN** the simulator pool has room and the user runs `shogun simulator start --wait`
- **THEN** the simulator is created and booted at once, without waiting

#### Scenario: Device already held
- **WHEN** the worktree already holds its emulator and the user runs `shogun emulator start --wait`
- **THEN** the emulator is started or reported as running without waiting, even if the pool is full

#### Scenario: Wait outside start
- **WHEN** the user runs `shogun emulator status --wait`
- **THEN** the command fails with a usage error saying `--wait` only applies to `start`, and exits with code 2

### Requirement: Bounded wait
A wait SHALL last at most 30 minutes by default. `--wait-timeout <minutes>` SHALL set that bound to a positive whole number of minutes and SHALL imply `--wait`. When the bound passes without acquiring, the command SHALL leave the queue, reserve and launch nothing, and fail with exit code 1 and an error that says how long it waited and lists the pool's holders as the pool-full error does. The bound SHALL only cover the wait for capacity, not the device's boot.

#### Scenario: Timeout
- **WHEN** every emulator stays protected by a working agent and the user runs `shogun emulator start --wait-timeout 5`
- **THEN** after 5 minutes the command fails with exit code 1, saying it waited 5 minutes and naming the worktrees that hold the emulators, and nothing is reserved

#### Scenario: Invalid timeout
- **WHEN** the user runs `shogun simulator start --wait-timeout 0`
- **THEN** the command fails with a usage error and exits with code 2

#### Scenario: Default bound
- **WHEN** the user runs `shogun simulator start --wait` and no simulator frees up
- **THEN** the command gives up after 30 minutes

### Requirement: Waiters are served in order
The waiters of a pool SHALL be served first in, first out, across every process and repository on the machine: only the waiter that has waited longest MAY acquire capacity of that pool. A waiter SHALL leave the queue when it acquires, times out, fails or is interrupted. A waiter whose process no longer runs SHALL be dropped from the queue the next time the queue is read.

#### Scenario: Two waiters
- **WHEN** the emulator limit is 1, `TASK-2` starts waiting before `TASK-3` and one emulator frees up
- **THEN** `TASK-2` acquires it and `TASK-3` keeps waiting, now first in the queue

#### Scenario: Interrupted waiter
- **WHEN** `TASK-2` is first in the queue and the user interrupts its command with Ctrl+C
- **THEN** `TASK-2` leaves the queue and `TASK-3` becomes first

#### Scenario: Waiter killed
- **WHEN** the process of the first waiter is killed without a chance to clean up
- **THEN** the next reading of the queue drops it and the next waiter becomes first

### Requirement: Acquisitions don't jump the queue
While other worktrees wait for a pool, an acquisition of that pool by a worktree that isn't first in its queue SHALL NOT take capacity: a start without `--wait`, or from the desktop app, SHALL fail with the pool-full error, which SHALL say how many worktrees are waiting. A worktree that already holds its device SHALL NOT be affected.

#### Scenario: Start without waiting behind a waiter
- **WHEN** `TASK-2` is waiting for an emulator, an emulator frees up and `TASK-4` runs `shogun emulator start` without `--wait` before `TASK-2` takes it
- **THEN** `TASK-4`'s start fails, saying one worktree is waiting, and `TASK-2` acquires the emulator

#### Scenario: Holder restarts its device
- **WHEN** worktrees are waiting for an emulator and `TASK-1`, which holds one, runs `shogun emulator start`
- **THEN** `TASK-1`'s emulator starts as usual

### Requirement: Show progress while waiting
While waiting, the command SHALL print on standard error, in text and JSON mode alike, a line with its place in the queue, the pool's limit, each holder with its state and when its lease expires or that its agent is working, and when the next lease expires. It SHALL print a new line only when any of that changes, and SHALL NOT print to standard output until the result.

#### Scenario: Start of a wait
- **WHEN** `TASK-2` starts waiting for the only emulator, held by `api` · `TASK-1` running with a lease until 14:32
- **THEN** standard error shows that `TASK-2` is first in the queue for 1 emulator, that `api` · `TASK-1` is running with its lease until 14:32, and that the next lease expires at 14:32

#### Scenario: Nothing changes
- **WHEN** the holders, the leases and the place in the queue stay the same for ten minutes
- **THEN** no new line is printed in those ten minutes

#### Scenario: Moving up
- **WHEN** the waiter ahead of `TASK-3` acquires its device
- **THEN** `TASK-3` prints a new line saying it is first in the queue

### Requirement: Waiting time in the result
After a start with `--wait`, the text output SHALL be the same as without it, and the command SHALL say on standard error how long it waited when it waited at all. With `--json`, the emulator and simulator objects SHALL include `waited`: the whole seconds spent waiting for capacity, `0` when there was room, and `null` for a command run without `--wait`. A timeout SHALL follow the errors in JSON mode: nothing on standard output.

#### Scenario: JSON after waiting
- **WHEN** the user runs `shogun emulator start --wait --json` and acquires an emulator after 95 seconds
- **THEN** standard output is one line with an object whose `serial` names the emulator and `waited` is `95`

#### Scenario: JSON without waiting
- **WHEN** the user runs `shogun simulator start --json`
- **THEN** the object's `waited` is `null`

#### Scenario: JSON timeout
- **WHEN** the user runs `shogun emulator start --wait-timeout 1 --json` and nothing frees up
- **THEN** standard output is empty, the error is on standard error and the exit code is 1

### Requirement: Show the waiters of each pool
`shogun pool status` SHALL list, under the emulator and simulator pools, the worktrees waiting for them in queue order, with the project, worktree and since when each waits. With `--json`, each pool object SHALL include `waiting`: an array of `{ worktree, since }`, empty when nobody waits.

#### Scenario: One waiter
- **WHEN** `main-repo` · `TASK-2` has been waiting for an emulator since 14:10 and the user runs `shogun pool status`
- **THEN** the emulator pool's lines include `main-repo` · `TASK-2` as waiting since 14:10

#### Scenario: Nobody waits in JSON
- **WHEN** nobody waits and the user runs `shogun pool status --json`
- **THEN** each device pool object has `waiting: []`

### Requirement: Tell agents how to wait
The workflow rules for the emulator and the simulator, shown in the session hint and by `shogun guide resources`, SHALL say that when the pool is full the agent can retry the start with `--wait`, run in the background or with a long enough shell timeout, because the wait can last up to the wait bound.

#### Scenario: Hint for the simulator
- **WHEN** a session starts in a worktree of a repository with the iOS simulator provider
- **THEN** the hint mentions `shogun simulator start --wait` for a full pool and running it in the background

#### Scenario: Guide
- **WHEN** the user runs `shogun guide resources` in a repository with the emulator provider
- **THEN** the output mentions `shogun emulator start --wait` for a full pool
