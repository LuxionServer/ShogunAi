# custom-pools Specification

## Purpose
Lets a repository declare in `.shogunai.json` its own limited resource shared between its worktrees, such as a database per worktree, a test device, a licence or a GPU, with commands to prepare and free an instance and the variables it gives the worktree, using the same leases, queue and reporting as the built-in pools.

## Requirements

### Requirement: Declare custom pools
`.shogunai.json` SHALL accept a top-level `pools` object whose keys are pool names and whose values declare `max`, `acquire`, and optionally `release` and `env`. A name SHALL be 1 to 32 lowercase letters, digits and hyphens, starting with a letter or digit. `max` SHALL be a whole number from 1 to 64, not a string. `acquire` and `release` SHALL each be one command given as a non-empty list of strings.

#### Scenario: Valid declaration
- **WHEN** `.shogunai.json` declares `{ "pools": { "db": { "max": 3, "acquire": ["./scripts/db-up.sh", "${slot}"], "release": ["./scripts/db-down.sh", "${slot}"], "env": { "DATABASE_URL": "postgres://localhost/app_${slot}" } } } }`
- **THEN** the repository has one pool `db` with 3 slots, its two commands and the variable `DATABASE_URL`

#### Scenario: No release command
- **WHEN** a pool declares `max` and `acquire` only
- **THEN** the pool is valid and freeing one of its slots runs no command

#### Scenario: Invalid name
- **WHEN** `pools` has a key `My DB`
- **THEN** creating and provisioning worktrees fail with a message naming `.shogunai.json` and the pool name

#### Scenario: Invalid max
- **WHEN** a pool declares `"max": 0`, `"max": 65` or `"max": "2"`
- **THEN** the file is invalid and the message names `pools.<name>.max`

#### Scenario: Command as a string
- **WHEN** a pool declares `"acquire": "./scripts/db-up.sh 1"`
- **THEN** the file is invalid and the message says the command must be a non-empty list of strings

### Requirement: Templates in pool commands and variables
Each argument of `acquire` and `release` and each value in a pool's `env` SHALL accept the templates `${slot}` (the holder's slot number), `${worktree}` (the holder's folder name) and `$$` (a literal `$`). Any other placeholder SHALL make the file invalid. Commands SHALL be run as the resulting argv, never through a shell.

#### Scenario: Slot in a command
- **WHEN** worktree `TASK-1` gets slot 2 of a pool whose `acquire` is `["./scripts/db-up.sh", "${slot}", "${worktree}"]`
- **THEN** the program `./scripts/db-up.sh` runs with the arguments `2` and `TASK-1`

#### Scenario: Shell characters stay literal
- **WHEN** an `acquire` argument is `app_${slot}; rm -rf ~`
- **THEN** the program receives the single argument `app_1; rm -rf ~` and no shell interprets it

#### Scenario: Unknown placeholder
- **WHEN** a pool's `env` declares `"URL": "http://localhost:${port}"`
- **THEN** the file is invalid and the message names the unknown placeholder

### Requirement: Pool variable names
A pool's variable names SHALL follow the rules of the repository's `env` names, including the reserved names, and SHALL NOT repeat a name declared in the repository's `env` or in another pool.

#### Scenario: Reserved name
- **WHEN** a pool declares the variable `PATH`
- **THEN** the file is invalid and the message says the name is reserved

#### Scenario: Name declared twice
- **WHEN** the repository's `env` declares `DATABASE_URL` and pool `db` also declares it
- **THEN** the file is invalid and the message names `DATABASE_URL`

### Requirement: A pool belongs to its repository
A custom pool's capacity, holders and queue SHALL be shared by the worktrees of the repository that declares it, and SHALL NOT be shared with another repository declaring a pool with the same name. The pool's limit SHALL be its `max`.

#### Scenario: Same name in two repositories
- **WHEN** `main-repo` and `api` each declare a pool `db` with `max` 1 and a worktree of each acquires it
- **THEN** both acquisitions succeed

#### Scenario: Limit from the declaration
- **WHEN** pool `db` declares `max` 2 and two worktrees of the repository hold it
- **THEN** a third worktree finds the pool full

### Requirement: Acquire a slot
`shogun pool acquire <name> [path]` SHALL give the worktree the lowest free slot of the pool, record its lease, and run `acquire` in the worktree's folder. When the command fails or times out, the slot SHALL be freed and the error SHALL name the command, its exit code and its error output. A worktree SHALL hold at most one slot of each pool.

#### Scenario: Free slot
- **WHEN** pool `db` has `max` 3, slot 1 is held and worktree `TASK-2` runs `shogun pool acquire db`
- **THEN** `TASK-2` gets slot 2, `acquire` runs with `${slot}` as `2`, and the command prints the pool, the slot and the lease expiry

#### Scenario: Acquire command fails
- **WHEN** `acquire` exits with code 1
- **THEN** the command fails with exit code 1, naming the command and its error output, and the slot is free again

#### Scenario: Unknown pool
- **WHEN** the user runs `shogun pool acquire cache` and the repository declares no pool `cache`
- **THEN** the command fails naming the pools the repository declares

### Requirement: Acquiring again renews the lease
When the worktree already holds a slot of the pool, `shogun pool acquire` SHALL keep the slot, renew its lease and run `acquire` again, so the command is safe to repeat.

#### Scenario: Renew
- **WHEN** `TASK-1` holds slot 1 of `db` with 3 minutes left on its lease and runs `shogun pool acquire db`
- **THEN** it keeps slot 1, the lease runs for the full lease duration again and `acquire` runs with `${slot}` as `1`

#### Scenario: Holder isn't queued
- **WHEN** the pool is full, other worktrees are waiting and its holder `TASK-1` runs `shogun pool acquire db`
- **THEN** `TASK-1` keeps its slot without waiting

### Requirement: Custom pool leases
Each slot SHALL have a lease that runs for the machine's lease duration from its last acquisition. A slot whose holder's agent is working SHALL be protected whatever its lease. A slot whose `acquire` is still running SHALL never be taken over.

#### Scenario: Lease expires
- **WHEN** `TASK-1` acquired slot 1 25 minutes ago, its agent has done nothing since and the lease duration is 20 minutes
- **THEN** its lease has expired and slot 1 can be taken over

#### Scenario: Working agent
- **WHEN** `TASK-1`'s lease has expired and its agent is working
- **THEN** its slot can't be taken over

### Requirement: Take over an expired slot when the pool is full
When the pool is full, acquiring SHALL take the slot of an expired holder, chosen in the order of release of the device pools, running `release` for the old holder first. When that `release` fails, the acquisition SHALL fail and the old holder SHALL keep its slot. The taken-over worktree SHALL record that it lost the slot, when and for which worktree.

#### Scenario: Take over
- **WHEN** `db` has `max` 1, `TASK-1` holds slot 1 with an expired lease and no agent session, and `TASK-2` runs `shogun pool acquire db`
- **THEN** `release` runs for `TASK-1` with `${slot}` as `1`, then `acquire` runs for `TASK-2`, and the result says the slot was taken from `TASK-1`

#### Scenario: Release fails during a take over
- **WHEN** the `release` run for `TASK-1` exits with code 1
- **THEN** `TASK-2`'s acquisition fails naming that command, and `TASK-1` still holds slot 1

#### Scenario: Taken-over worktree explains itself
- **WHEN** `TASK-1` lost slot 1 to `TASK-2` and the user runs `shogun status` in `TASK-1`
- **THEN** the line of `TASK-1` says its `db` slot was taken for `TASK-2`

### Requirement: Fail clearly when the pool is full
When the pool is full and no slot can be taken over, acquiring without `--wait` SHALL fail with exit code 1 and change nothing, with an error that states the limit, names each holder with its slot and when its lease expires or that its agent is working, says how many worktrees are waiting, and points to `--wait` and `shogun pool release`.

#### Scenario: All slots protected
- **WHEN** `db` has `max` 2, both slots have valid leases and `TASK-3` runs `shogun pool acquire db`
- **THEN** the command fails listing both holders with their lease expiry and pointing to `--wait`

### Requirement: Wait for a slot
`shogun pool acquire` SHALL accept `--wait` and `--wait-timeout <minutes>` with the meaning and bounds they have for emulators. The waiters of a pool SHALL be served first in, first out, an acquisition without `--wait` SHALL NOT take capacity while others wait, and progress SHALL be printed on standard error only when it changes.

#### Scenario: Slot frees up
- **WHEN** `db` has `max` 1, `TASK-1`'s lease expires in 3 minutes and `TASK-2` runs `shogun pool acquire db --wait`
- **THEN** the command waits, takes the slot over once the lease expires and exits with code 0

#### Scenario: Timeout
- **WHEN** the only slot stays protected and the user runs `shogun pool acquire db --wait-timeout 5`
- **THEN** after 5 minutes the command fails with exit code 1, saying how long it waited and naming the holder, and nothing is held

#### Scenario: Don't jump the queue
- **WHEN** `TASK-2` waits for `db`, a slot frees up and `TASK-3` runs `shogun pool acquire db` without `--wait`
- **THEN** `TASK-3`'s acquisition fails saying one worktree is waiting

#### Scenario: Wait outside acquire
- **WHEN** the user runs `shogun pool release db --wait`
- **THEN** the command fails with a usage error and exit code 2

### Requirement: Release a slot
`shogun pool release <name> [path]` SHALL run the pool's `release` in the worktree's folder and free the worktree's slot. When `release` fails, the slot SHALL stay held and the command SHALL fail naming the command. Releasing a pool the worktree doesn't hold SHALL succeed and say nothing was held.

#### Scenario: Release
- **WHEN** `TASK-1` holds slot 2 of `db` and runs `shogun pool release db`
- **THEN** `release` runs with `${slot}` as `2`, the slot is free and the command says so

#### Scenario: Release fails
- **WHEN** `release` exits with code 1
- **THEN** the command fails with exit code 1 naming the command, and `TASK-1` still holds slot 2

#### Scenario: Nothing held
- **WHEN** `TASK-1` holds no slot of `db` and runs `shogun pool release db`
- **THEN** the command exits with code 0 saying it held none

### Requirement: Release slots when a worktree is removed
Removing a worktree SHALL run `release` for each slot it holds, before its folder is removed, free those slots whatever the result, drop the worktree from the pools' queues and clear its loss records. A failing `release` SHALL be reported without stopping the removal.

#### Scenario: Remove a holder
- **WHEN** `TASK-1` holds slot 1 of `db` and the user runs `shogun remove TASK-1`
- **THEN** `release` runs in `TASK-1`'s folder, the worktree is removed and slot 1 is free

#### Scenario: Release fails on removal
- **WHEN** that `release` exits with code 1
- **THEN** the worktree is still removed, slot 1 is free and the failure is reported on standard error

### Requirement: Only approved repositories use their pools
Acquiring, releasing and taking over SHALL run a pool's commands only when the repository is approved. Otherwise `shogun pool acquire` and `shogun pool release` SHALL fail without running anything, naming `shogun trust`. On worktree removal an unapproved repository's slots SHALL be freed without running `release`, with a note naming `shogun trust`.

#### Scenario: Not approved
- **WHEN** a pull added pool `db` and the user hasn't approved the repository since, and runs `shogun pool acquire db`
- **THEN** the command fails with exit code 1, runs nothing and names `shogun trust`

#### Scenario: Removal while not approved
- **WHEN** `TASK-1` holds a slot, the repository's approval was revoked and the user removes `TASK-1`
- **THEN** the slot is freed, `release` doesn't run and standard error names `shogun trust`

### Requirement: Deliver the pool's variables
While a worktree holds a slot and the repository is approved, the pool's variables SHALL be part of the worktree's environment wherever the declared variables are delivered, rendered with its slot. They SHALL NOT be delivered for a pool the worktree doesn't hold.

#### Scenario: Holder
- **WHEN** `TASK-1` holds slot 2 of `db`, which declares `DATABASE_URL` as `postgres://localhost/app_${slot}`, and runs `shogun env`
- **THEN** the output sets `DATABASE_URL` to `postgres://localhost/app_2`

#### Scenario: Not held
- **WHEN** `TASK-1` holds no slot of `db` and runs `shogun env`
- **THEN** the output doesn't set `DATABASE_URL`

### Requirement: Custom pools in pool status
`shogun pool status` SHALL list, after the built-in pools, each custom pool of the current repository with its slots in use over `max`, each holder with its slot, state and lease expiry or protection, and its waiters in queue order. With `--json` the object SHALL include `customPools`: an array of `{ name, max, holders, waiting }`.

#### Scenario: One holder and one waiter
- **WHEN** `db` has `max` 1, `TASK-1` holds slot 1 and `TASK-2` waits since 14:10
- **THEN** the output shows `db 1/1`, `TASK-1` with slot 1 and its lease expiry, and `TASK-2` waiting since 14:10

#### Scenario: Repository without pools
- **WHEN** the repository declares no pools and the user runs `shogun pool status --json`
- **THEN** `customPools` is an empty array

### Requirement: Custom pools in worktree status
`shogun status` SHALL show on each worktree's line the custom pool slots it holds, the pools it waits for with its place in line, and a slot it lost. With `--json` every worktree object SHALL include `pools`: an array of `{ "name", "role": "holder", "slot", "expiresAt" }`, `{ "name", "role": "waiting", "position", "since" }` or `{ "name", "role": "lost", "at", "forWorktree" }`, with ISO-8601 times.

#### Scenario: Holder in JSON
- **WHEN** `TASK-1` holds slot 1 of `db` and the user runs `shogun status --json`
- **THEN** `TASK-1`'s `pools` is `[{ "name": "db", "role": "holder", "slot": 1, "expiresAt": "<ISO-8601>" }]`

#### Scenario: Nothing held
- **WHEN** a worktree holds and waits for no custom pool
- **THEN** its `pools` is an empty array and its text line shows nothing about pools

### Requirement: Tell agents about the repository's pools
The session hint and `shogun guide resources` SHALL, in a repository that declares pools, name each pool and say to take a slot with `shogun pool acquire <name> --wait` before using it, run in the background or with a long enough shell timeout, and to free it with `shogun pool release <name>`.

#### Scenario: Hint
- **WHEN** a session starts in a worktree of a repository declaring pool `db`
- **THEN** the hint mentions `shogun pool acquire db --wait` and `shogun pool release db`

#### Scenario: No pools
- **WHEN** the repository declares no pools
- **THEN** the hint says nothing about custom pools
