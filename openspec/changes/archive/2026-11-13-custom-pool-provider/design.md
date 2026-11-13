# Design

## Context

See proposal.md for the motivation. What already exists and gets reused:

- **Physical devices** (`PhysicalDeviceUseCases.kt`): the closest model. A machine-wide lease file (`~/.shogunai/device-leases.json`, `PhysicalDeviceLeaseStore`) written in one read-modify-write under the state store's lock, take-over of an expired lease ranked by `releaseRank`, a loss record on the taken-over worktree, `WaitInLine` for `--wait`, `variablesFor` wired into `ResolveWorktreeEnvironmentUseCase` through `deviceVariables`, and `onWorktreeRemoved` called from `WorktreeFolderRemoval`.
- **Waiting**: `WaitInLine` and `DeviceWaitQueue` over `~/.shogunai/device-queue.json`, keyed by `WaitQueueName`. Physical devices share one queue per platform and keep each waiter's filter in `WaitTicket.request`.
- **Leases**: `ResourceSettings.leaseDuration` and the agent state from Claude Code's hooks (`readAgentActivity`).
- **Trust**: `RepositoryApprovalSubject` and `JsonRepositoryTrust`, which hashes a canonical JSON of the subject and keeps the old digest (commands only) when no environment is declared, so earlier approvals still hold.
- **Configuration**: `RepositoryProvisioningFiles` parses `.shogunai.json` with hand-written checks per key, and unknown keys are ignored, so older ShogunAi versions ignore `pools`.

Three parallel changes touch nearby code: `device-lock-scope` (what runs under the machine lock when acquiring emulators and simulators), `build-turn` (a turn for heavy builds on the same queue) and `node-provider` (the provider registry and the `.shogunai.json` parser). This change uses the queue, leases and lock as they are and adds to shared files only the lines it needs.

## Goals / Non-Goals

**Goals:**

- A repository can declare a resource and its commands without code in ShogunAi, and get leases, take-over, `--wait`, release on removal and reporting like the built-in pools.
- Repository commands run only after the same approval as post-creation commands, and always as argv.
- No cost for repositories that declare no pools: nothing is read or written for them beyond parsing `.shogunai.json`.

**Non-Goals (roadmap):**

- A per-machine setting that lowers a pool's `max`.
- A desktop app view of custom pools (the Machine view, the worktree detail and row) and reporting a failed `release` on removal from the app.
- `${port}` and `${port:<key>}` in pool templates.
- Pools shared by several repositories (machine-wide names such as a GPU or a licence).
- Acquiring a slot automatically when a worktree is created.

## Decisions

### Declaration

```json
"pools": {
  "db": {
    "max": 3,
    "acquire": ["./scripts/db-up.sh", "${slot}"],
    "release": ["./scripts/db-down.sh", "${slot}"],
    "env": { "DATABASE_URL": "postgres://localhost/app_${slot}" }
  }
}
```

A top-level key rather than a `providers` entry, as the user decided: providers are ShogunAi's own integrations with fixed options, while `pools` is a map of names the repository chooses. Slots rather than command output: ShogunAi gives each holder a number from 1 to `max`, passed as `${slot}`, so the variables are fully known from the templates and the user sees exactly what will be delivered when approving. An `acquire` that prints the variables would hide their values from the approval.

Templates are parsed by a new `parsePoolTemplate` that accepts `${slot}`, `${worktree}` and `$$`, kept apart from `parseEnvironmentVariable` so the repository's `env` placeholders don't change. Pool variable names reuse `isValidVariableName` and `isReservedVariableName`. The parser lives in a new `CustomPoolDeclarations.kt` in infrastructure; `RepositoryProvisioningFiles.read` gains one line that calls it with the `env` names already parsed, to reject duplicates.

Limits on the declaration: names `[a-z0-9][a-z0-9-]{0,31}`, because they appear in queue tickets, CLI arguments and JSON; `max` from 1 to 64, which no real resource on a developer machine exceeds and which keeps a mistyped `6400` from meaning "no limit".

### A repository's pools

The pool's identity is the repository's Git common directory plus the pool name, as the user decided. The common directory is what `ResolveWorktreeEnvironmentUseCase.directoriesOf` already returns, and the same for every worktree of the repository, wherever its folder is.

The limit is the declared `max`. The `resource-pools` requirement "Limits come from the machine, not the repository" gets an explicit exception for custom pools, since the repository is the one that knows how many instances its resource allows. A machine override is a roadmap item.

### Lease store

A new port `CustomPoolLeaseStore` with `NioCustomPoolLeaseStore` over `~/.shogunai/pool-leases.json`, written atomically and only under `EnvironmentStateStore.withLock`, like `device-leases.json`. Each lease records the repository's common directory, the base repository path, the pool name, the slot, the holder's admin directory and worktree path, `acquiredAt`, `renewedAt` and `runningBy`, the process identity of a command still running for that slot. Losses record the worktree, pool, slot, time and beneficiary, and are cleared when that worktree acquires the pool again or is removed.

Alternative: storing the slot in each worktree's environment state, like the emulator port. Rejected for the reason physical devices rejected it: exclusivity would depend on scanning every worktree.

### Acquiring

External commands never run under the lock, since `acquire` may take minutes (starting a container). The lock only guards each change to the store:

1. Read `.shogunai.json` from the base repository, find the pool and check the approval; fail before touching anything otherwise.
2. Under the lock: if the worktree holds the pool, set `renewedAt` and `runningBy`. Otherwise, unless other waiters are ahead, take the lowest free slot from 1 to `max`, or choose an expired holder with `releaseRank(stopped = true, busy = running, ...)`, least recently renewed first within a rank, and move the lease to the new holder with `runningBy` set, remembering the previous lease. If nothing can be taken, fail with `CustomPoolFull`.
3. On a take-over, run `release` for the previous holder, in its worktree folder or the base repository when the folder no longer exists. If it fails, give the lease back under the lock (only if it is still ours) and fail.
4. Run `acquire` in the worktree's folder. If it fails, drop the lease under the lock (only if it is still ours), and for a renewal keep nothing either: a failing `acquire` leaves the worktree without the slot, which is what its holder would expect after an error.
5. Under the lock: clear `runningBy`, set `renewedAt`, record the loss of the previous holder.

A lease whose `runningBy` process is alive is never taken over; one whose process is gone counts as not running, so a killed `shogun pool acquire` doesn't block a slot past its lease.

Commands use `CommandTimeouts.INSTALL` (30 minutes), as post-creation commands do, and run through `ShellCommandExecutor.executeWithin` as argv after rendering the templates in each argument.

Holders whose slot is above a lowered `max` keep their slot until they release it or are removed, count as in use, and are never handed over, so lowering `max` frees capacity as holders leave.

### Waiting

One new `WaitQueueName.CUSTOM_POOL("custom-pool")` entry holds every custom pool's waiters, each ticket's `request` being `<common directory>\n<pool name>`, the same way physical devices keep one queue per platform with a filter per ticket. Only tickets of the same pool count as ahead, and the place in line shown in progress is computed among them. This adds one enum line to shared code instead of turning `WaitQueueName` into an open key, which `build-turn` and `device-lock-scope` also touch. `--wait` and `--wait-timeout` reuse `DeviceWaitOptions` and `WaitInLine.await` unchanged.

### Releasing

`shogun pool release` runs `release` under no lock, then drops the lease under the lock; a failure keeps the lease. On worktree removal, `ReleaseCustomPoolUseCase.onWorktreeRemoved` runs `release` for each slot the worktree holds (in its folder, which still exists at that point), drops the leases whatever happens, clears its losses and drops its tickets. It returns the failures as `WorktreeError`s, which reach `shogun remove` and `shogun clean` through a new `poolReleaseFailures` field of `HolderClosing`, `WorktreeRemoval` and the cleanup report, printed on standard error. An unapproved repository's slots are freed without running `release`, reported as a failure naming `shogun trust`.

`WorktreeFolderRemoval`, `RemoveWorktreeUseCase` and `CleanUpWorktreesUseCase` take the use case as a new optional last parameter, passed by `ProjectEngine`, next to `releasePhysicalDevices`. The Orca removal hook in `ShogunCli` calls it too.

### Variables

`CustomPoolLedger.variablesFor(worktreePath)` returns nothing without reading anything else when the lease file has no lease for the worktree; otherwise it reads the repository's pools, checks the approval and renders each held pool's `env` with its slot. `ShogunEngine` passes `physicalDeviceLedger.variablesFor(it) + customPoolLedger.variablesFor(it)` as `deviceVariables`, so every place that delivers the environment (`shogun env`, the session hint, terminals, Herdr and Orca) gets them without new plumbing. As with physical devices, the environment file isn't rewritten on acquire; shells already open read the variables with `shogun env`.

### Trust

`RepositoryApprovalSubject` gains `pools: List<CustomPool>`. `JsonRepositoryTrust` adds a `pools` member to the canonical JSON only when pools are declared, with pools sorted by name and each pool's variables sorted by name, so a repository without pools keeps its digest and reordering doesn't ask again. When pools are declared and no environment is, the canonical form is used (instead of the commands-only digest) so the pools are part of it. The stored approval doesn't copy the pools next to the digest: the digest is what decides, and a new optional field in the stored record would only add a serializer branch no input reaches.

`isEmpty` counts pools, so `shogun trust` asks for a repository that only declares pools. The desktop app's save rule ("approved before, or declares no post-creation commands") also requires no pools. `ApprovalSubjectLines` shows each pool's name, `max`, commands and variables; the creation dialog and project settings already use it.

### Reporting

`shogun pool status` appends the current repository's custom pools after the built-in ones; `--json` adds `customPools`. `shogun status` adds the slots held, waited for and lost per worktree, and `pools` to every worktree object. Both read the lease file once and the queue once. `shogun trust` prints the pools and `--json` adds `pools`. The session hint and `shogun guide resources` get one rule, shown only when the repository declares pools, naming them (`WorkflowCondition.CUSTOM_POOLS`, `WorkflowContext.customPools`).

### Where the code goes

New files: `domain/model/CustomPool.kt` (declaration, template, lease, loss, state), `domain/model/CustomPoolErrors.kt` (one `WorktreeError` subclass, `CustomPoolFailure`, carrying a sealed `CustomPoolProblem`: unknown pool, not approved, full, wait timed out, command failed; `WorktreeError.kt` is at its length cap, and the desktop app's exhaustive error texts then need one branch per language instead of five), `domain/io/CustomPoolLeaseStore.kt`, `domain/usecase/CustomPoolUseCases.kt` (ledger, reading, variables) and `domain/usecase/CustomPoolSlotUseCases.kt` (acquire and release), `infrastructure/CustomPoolDeclarations.kt`, `infrastructure/NioCustomPoolLeaseStore.kt`, in the CLI `CustomPoolCommand.kt` (parsing, with `UsageException` made `internal` so the new file can throw it), `CustomPoolText.kt` and `CustomPoolJson.kt`, and in the desktop app `ui/i18n/CustomPoolErrorTexts.kt`.

### File length caps

These files are exactly at their cap and need a few lines each; their caps rise by as many:

- `cli/CliCommand.kt` 1090 → 1091: `parsePool` delegates `acquire` and `release` to the new parser.
- `cli/CommandJson.kt` 1028 → 1029: `trustJson` adds `pools`.
- `infrastructure/RepositoryProvisioningFiles.kt` 459 → 460: `read` adds `pools`.
- `ui/i18n/EnglishErrors.kt` 479 → 481 and `ui/i18n/SpanishErrors.kt` 500 → 502: the import and the branch of `CustomPoolFailure` in the exhaustive `when`.
- `ui/i18n/Strings.kt` 956 → 958, `ui/i18n/EnglishStrings.kt` 1129 → 1131 and `ui/i18n/SpanishStrings.kt` 1127 → 1129: the approval's pool title and pool line.

The alternative, moving existing code out of those files to make room, is a refactor outside this change and would conflict with the parallel changes editing them.

### Coverage

`core` keeps 100% of lines and its maximum of missed branches, unchanged: the store's serializable entries declare no defaults, so kotlinx.serialization generates no unreachable branch, and the trust record gained no field. `cli` keeps 100% of lines and branches.

## Risks / Trade-offs

- **A slow `release` on removal delays the removal** → bounded by the 30-minute command limit, like post-creation commands; the guide recommends keeping it quick.
- **A slot changes hands while a command runs elsewhere** → every write after a command re-reads the store under the lock and only changes a lease that is still ours.
- **A repository declares a pool for a resource of the whole machine** (a GPU) → each repository counts its own slots, so two repositories can oversubscribe it. Documented, and machine-wide pools are on the roadmap.
- **`acquire` runs again on every renewal** → documented: commands must be safe to repeat, as post-creation commands already are.

## Migration Plan

Nothing to migrate: without `pools` nothing changes, approvals keep their digests, and `pool-leases.json` is created on the first acquisition. Older ShogunAi versions ignore the `pools` key.
