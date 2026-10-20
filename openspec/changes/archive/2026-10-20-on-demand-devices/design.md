# Design

## Context

The emulator provider reserves an even console port (5554–5584, 16 pairs) and the iOS simulator provider creates a simulator the first time a worktree's environment is resolved with reservation, which happens when the worktree is created (`PrepareWorktreeEnvironmentUseCase`), on `shogun env` and in the `SessionStart` hook. Both go through `ResolveWorktreeEnvironmentUseCase`, which takes `PortRequest`s and `ResourceRequest`s from `EnvironmentContribution`s and stores them in each worktree's Git admin directory (`shogunai-env.json`) through `EnvironmentStateStore`, which also keeps a machine index (`~/.shogunai/reservations.json`) and a cross-process file lock.

Gradle daemons are started by agents through `./gradlew`; ShogunAi only lists them (`GradleDaemonSource`) and stops idle ones on request (`StopGradleDaemonsUseCase`). Nothing bounds how many emulators, simulators or daemons are alive.

App settings live in `~/.shogunai/settings.json` (`JsonSettingsFile.Settings`), read through small `Json*PreferenceRepository` classes in `core`. The CLI doesn't read them today.

The user decided, before this design:

- Limits are machine settings, never in `.shogunai.json`; the repository only declares what it needs.
- Profiles: Automatic (default, from memory and cores), Light, Balanced, One per worktree, Custom (a number per kind).
- The pool covers emulators, iOS simulators and Gradle daemons, all counted in units.
- When full: release the least-recently-used idle resource; if none is idle, fail with an error naming each holder.
- DerivedData's disk quota is left for a later change.

## Goals / Non-Goals

**Goals:**

- Devices are acquired when a worktree first starts one, not when it's created.
- One pool model shared by the three providers, with limits from a machine profile.
- The same limits and behaviour from the desktop app and the `shogun` command.
- No device a worktree already holds is lost on upgrade.

**Non-Goals:**

- DerivedData quota (disk-based; follow-up reusing the model).
- Kotlin daemons in any pool.
- Deciding whether an agent is using a running device: running and booting devices are never released automatically.
- Starting devices automatically when an agent needs them (the agent runs `shogun emulator start` / `shogun simulator start`).
- Trimming Gradle daemons from hooks or from every `shogun` command.

## Decisions

### 1. Acquire on start, read on resolve

`PortRequest` and `ResourceRequest` gain an `onDemand` flag. `ResolveWorktreeEnvironmentUseCase` with `reserve = true` keeps reserving the declared variable ports and non-on-demand contributions, but for on-demand ones it only reads what's stored, as it does today with `reserve = false`. The emulator and simulator contributions become on-demand. The "all keys present" check that today returns an environment without variables when a reservation is missing is narrowed to the keys that must be present, so a worktree without a device still gets its other variables, and the provider's variables are emitted only when its key is present.

Acquisition is a new `AcquireWorktreeResourceUseCase` called by `StartWorktreeEmulatorUseCase` and the simulator start use case before they do anything else. After acquiring, it rewrites the environment file (`WriteEnvironmentFileUseCase`) so a declared `envFile` picks up the new variable.

*Alternative considered*: keep reserving at creation but start nothing. Rejected: the port range (16) and simulator disk are exactly what on-demand saves.

### 2. A pool is computed, not stored

There's no separate pool registry. A pool's holders are read from the machine's existing reservations: every admin directory in the machine index (plus the current repository's) with an `emulator` port or an `ios-simulator` resource. For Gradle, holders are the running Gradle daemons with their worktree attribution. This keeps one source of truth, survives `git worktree remove` (the admin directory disappears, and its reservation with it) and needs no migration.

Model (in `domain/model`):

- `ResourceKind { EMULATOR, IOS_SIMULATOR, GRADLE_DAEMON }`
- `CapacityProfile { AUTOMATIC, LIGHT, BALANCED, PER_WORKTREE, CUSTOM }` and `ResourceLimits(emulators: Int?, simulators: Int?, gradleDaemons: Int?)` where `null` is no limit.
- `PoolHolder(kind, worktreePath, adminDirectory, state, idle, lastUsed: Instant?)` and `PoolUsage(kind, limit: Int?, effectiveLimit: Int?, holders)`.
- `PoolRelease(kind, worktreePath, reason)` reported back by acquisition and trimming.

`effectiveLimit` applies the platform bound: for emulators `min(limit ?: 16, 16)`.

### 3. Last use stored with the reservation

`shogunai-env.json` gains a `lastUsed` object (`key → epoch millis`), written through two new `EnvironmentStateStore` methods (`readLastUsed`, `writeLastUsed`) and a `reservationsOnMachine(commonDirectory)` that returns, per admin directory, its ports, resources and last-use times. Every start (acquiring or not) updates the time. Missing times sort first (oldest), which is what makes devices reserved before this change the first to be reclaimed.

### 4. Acquisition under the reservation lock

`AcquireWorktreeResourceUseCase(kind)`:

1. Read the limit (`ResourceLimitsRepository`, resolved through `CapacityProfile`).
2. Under `stateStore.withLock`: if the worktree already holds the resource, touch its last use and return. Otherwise read the holders; if `holders < effectiveLimit` (and, for emulators, a free port pair exists), reserve. Otherwise pick the idle holder with the oldest `lastUsed` (emulator: not in `adb devices`; simulator: `Shutdown` in `simctl`), release it, then reserve. If no holder is idle, throw `WorktreeError.ResourcePoolFull(kind, limit, holders)`.
3. Return the reservation plus the `PoolRelease`s made.

Releasing an emulator removes its port from the holder's reservations (a read-only AVD keeps no state, so nothing is lost). Releasing a simulator deletes it with `simctl delete` and removes its UDID.

The simulator's `simctl create` runs inside the lock. Today the resource request creates outside the lock and resolves races afterwards; holding the lock for a few seconds is simpler and makes the count exact. Device states (`adb devices`, `simctl list`) are read once per acquisition, before taking the lock, and re-checked for the chosen victim inside it.

*Alternative considered*: a "pending" placeholder written under the lock and replaced after creation. Rejected for the extra stale-entry handling; acquisitions are rare and user-initiated.

### 5. Errors name the holders

`WorktreeError.ResourcePoolFull` carries each holder's worktree path and state; the presentation layer (`error-presentation`) maps the path to `project · worktree` using the saved projects when it can, otherwise the repository and worktree folder names. The existing `NoEmulatorPort` is kept for the case where all ports are busy on the machine for reasons outside ShogunAi (e.g. Android Studio emulators on those ports) and no reservation can be reclaimed.

### 6. Limits from a profile, resolved per machine

`ResourceSettings(profile: CapacityProfile = AUTOMATIC, custom: ResourceLimits = …)` is added to `JsonSettingsFile.Settings` as `resources`, read and written by a new `JsonResourceLimitsRepository` in `core/infrastructure`, which both the desktop app and the CLI build over `~/.shogunai/settings.json`. A missing or unreadable value means Automatic, so the CLI never fails because of Preferences.

`ResolveResourceLimitsUseCase` turns the profile into `ResourceLimits` using a `MachineCapacity` port (`totalMemoryBytes(): Long?`, `cores(): Int`). The JVM implementation uses `com.sun.management.OperatingSystemMXBean.totalMemorySize` and `Runtime.availableProcessors()`, so no shell command and the same code on every platform.

| Profile | Emulators | Simulators | Gradle daemons |
|---|---|---|---|
| Automatic | `clamp(min((GB − 8) / 8, cores / 4), 1, 4)` | same as emulators | `clamp(GB / 8, 2, 8)` |
| Light | 1 | 1 | 2 |
| Balanced | 2 | 2 | 4 |
| One per worktree | no limit | no limit | no limit |
| Custom | user | user | user |

Integer division throughout. Unknown memory falls back to Light. The numbers are a starting point; changing them later changes no contract other than the scenarios that cite them.

### 7. Gradle daemons are trimmed, not acquired

ShogunAi doesn't start Gradle daemons, so it can't refuse one. `TrimGradleDaemonsUseCase` lists daemons, and when Gradle daemons exceed the limit stops idle ones, longest idle first, through `StopGradleDaemonsUseCase` (which re-checks idleness before stopping). The desktop app runs it in the existing 30-second machine summary refresh, only when a saved project declares the Gradle provider; `shogun pool trim` runs it on demand. The trim's stops are kept in the machine state and shown in the Gradle view until dismissed.

### 8. Delivering the variable after acquisition

An agent's shell got its environment at session start, without the device variable. `shogun emulator start` / `shogun simulator start` print, after acquiring, the `export …` line on standard output (after the status line) so the agent can run it; the session-start context explains this. The app's "Open in terminal/Herdr" already resolves the environment when launching, so terminals opened after the start get the variable.

### 9. CLI surface

`shogun pool status [--json]` and `shogun pool trim [--json]`, parsed like `gradle` (`pool` added to the JSON commands). `emulator status` / `simulator status` print `not acquired` when there's nothing reserved.

### 10. Desktop UI

- Preferences → new "Resources" section: a segmented or radio choice of profile, a line per kind showing the limits it gives on this machine, and three number fields (with a "no limit" toggle) enabled only for Custom. The iOS simulator line appears only on macOS.
- Machine views: a usage line ("1 of 2 in use · Automatic", with a link to Preferences), an over-limit mark, and a dismissable notice for releases and trims. Sidebar summaries add "n/limit".
- Worktree detail and machine emulator/simulator rows show "Not acquired" with Start.

## Risks / Trade-offs

- [Agents that relied on `ANDROID_SERIAL` / `IOS_SIMULATOR_UDID` being present from session start] → session-start context and `start` output tell them how to get it; existing worktrees keep their reservations, so only new worktrees change.
- [Releasing a simulator deletes the app installed in it] → only shut-down simulators are released, least recently used first, and the release is reported; One per worktree avoids it entirely.
- [Holding the reservation lock during `simctl create` delays other `shogun env` calls by a few seconds] → acquisitions are rare and user-initiated; reads without reservation don't take the lock.
- [Automatic limits too strict or too loose] → the formula is in one use case with tests per memory size; Custom is always available.
- [Trimming Gradle daemons automatically may surprise users who start the app] → only idle daemons over the limit are stopped, the existing re-check prevents stopping a daemon that became busy, and the view reports each stop.
- [Emulators started outside ShogunAi occupy console ports] → they don't count against the pool but still make ports busy; the existing `NoEmulatorPort` error covers it.

## Migration Plan

No data migration. Stored ports and simulator UDIDs count as acquired; their missing `lastUsed` makes them the first idle candidates. Settings without `resources` read as Automatic. Rollback is reverting the change: older versions keep reading the same `ports`/`resources` fields and ignore `lastUsed`.

## Open Questions

- Whether `shogun hook session-start` should also trim Gradle daemons for users who never run the desktop app. Left out for now; `shogun pool trim` covers it on demand.
