# Design

## Context

See proposal.md for the measurements. Where the processes come from today:

- `WorktreeBoard.startWatching` loops on a fixed `AUTO_REFRESH_INTERVAL` (3 s) while `windowActive` is true. `windowActive` is fed from `main.kt`'s `windowShown` (visible, not minimized, app not hidden), so a visible unfocused window polls as fast as a focused one. `AppShell` already reads `LocalWindowInfo.isWindowFocused`, but only to trigger `refreshOnFocus`.
- Each check runs `DetectRunningAgentsUseCase` once for all loaded projects. Its process scan starts with a machine-wide `lsof -d cwd -F pcn` (every process's working directory, ~0.1 s of system CPU), then `ps -o pid=,comm= -p <pids in worktrees>`, and filters by executable name last.
- `ProjectWorktrees.refresh()` (a full reload) calls `useCases.invalidateEnvironments()`, which clears the whole `WorktreeEnvironmentCache`, shared by every project. The next `refreshDevices` reads each worktree's background app state through `HeadlessAppWorktrees.pathsOf` → `ResolveWorktreeEnvironmentUseCase.directoriesOf`, which misses the cache and runs `git rev-parse --git-dir --git-common-dir` per worktree.
- `checkFingerprint` compares the whole list of modification stamps (`ReadGitMetadataFingerprintUseCase`) and any difference triggers a full reload: list, statuses, agents, ports, devices, sizes queue, sections. With agents working, linked worktrees' `index` files change all the time (agents and their tools run `git status`, `git add`), so a busy project reloads often.
- `MachineResources` refreshes the shown machine view every `PROCESS_INTERVAL` (5 s) and the sidebar summaries every `SUMMARY_INTERVAL` (30 s), both gated by the same "shown" flag.
- `ShogunCli.status` reads every worktree's status in a `map`, one after another, after detecting agents and reading bases, also one after another.
- `CapacityProfile` and `ResolveResourceLimitsUseCase` (core) already read the machine's memory and cores through `MachineCapacity`, and the settings through `ResourceLimitsRepository`; `ResourcePreferences` (desktop) holds the chosen profile as Compose state.

## Goals / Non-Goals

**Goals:**
- Fewer and cheaper processes per check, independently of the profile.
- An idle app whose cadence can be lowered by the user today, with the existing profile choice, and that lowers itself on battery when left on Automatic.
- A guard in the normal test run against reintroducing per-cycle work, and a manual benchmark to measure the real effect.

**Non-Goals:**
- Filesystem watchers instead of polling Git metadata: stays on the roadmap.
- A separate refresh setting in Preferences: deliberately not added (see Decision 5).
- Startup time and memory of the app (measured fine, out of scope).
- Reducing the cost of a full reload itself (list, overview sections), beyond not triggering it for index-only changes.

## Decisions

### 1. Agent detection: candidate processes by name first, `lsof` only for them

`scanProcesses` is reordered: first `ps -A -o pid=,comm=` (one cheap listing of every process's executable), keep the pids whose executable name is a known agent or one of the searched projects' agent commands, and only if there is any, run `lsof -a -d cwd -F pcn -p <candidate pids>`. Attribution to worktrees (longest path, nested repositories, exact searches) and the Claude pid set stay as they are, applied to the restricted set. With no agent running nothing but `ps` runs; with a few agents `lsof` inspects a few processes instead of all of them.

The result is the same as today because today's filter keeps only processes whose executable name is an agent name anyway; only the order of the two filters changes.

Alternatives: spacing detection out to every Nth check (makes agent states, the most useful information, staler, and still pays the full `lsof`); using Claude Code's session registry for working directories (only covers Claude); reading `/proc/<pid>/cwd` on Linux (a later optimization, not needed for the macOS cost measured).

### 2. Git directories cached per worktree, validated by its `.git` entry

`WorktreeEnvironmentCache` keeps, per worktree path, the resolved admin and common directories together with a cheap validation key: the content of the worktree's `.git` file for a linked worktree (`gitdir: …`), or a marker that `.git` is a directory for a main worktree. A lookup reads that entry (a file read, no process) and reuses the cached directories when it matches; otherwise it runs `git rev-parse` once and stores the new pair. `ProjectWorktrees.refresh()` stops calling `invalidateEnvironments()`: a worktree removed and created again at the same path gets a different `.git` file and is resolved again. The explicit invalidation after provisioning stays (rare, user-triggered).

The `.git` reading goes through a function parameter of the use case, as `DetectRunningAgentsUseCase` already does with `hasGitEntry`, so tests can fake it.

Alternatives: keep global invalidation but only on list changes (still re-resolves every project on any reload); resolving directories by parsing `.git` and `commondir` ourselves (duplicates Git's rules for relative paths and extensions; `git rev-parse` stays the source of truth on a miss).

### 3. Index-only changes re-read statuses, not the whole project

`checkFingerprint` diffs the previous and new stamp lists by path. If every changed, added or removed path is an index file (`index`, `index.lock`, `worktrees/<name>/index`, `worktrees/<name>/index.lock`), it calls `refreshStatuses()` (the same light path `refreshOnFocus` uses) and returns a new `FingerprintCheck.STATUSES_REFRESHED`, which counts as unchanged for agent detection and device refresh scheduling. Anything else keeps today's full reload. Re-reading all the project's statuses rather than only the worktree whose index changed keeps the mapping from `worktrees/<name>` to a path out of the picture; status reads are already concurrent in the app.

### 4. The cadence is a derived core model

A new `RefreshCadence` (core `domain/model`) holds five durations: `focusedCheck`, `unfocusedCheck`, `devices`, `machineView`, `summaries`, with `LIGHT`, `BALANCED` and `PER_WORKTREE` constants carrying the values of the `refresh-cadence` spec. A new `ResolveRefreshCadenceUseCase` (core), built from `ResourceLimitsRepository`, `MachineCapacity` and a new `PowerSource` port, maps the stored profile: Light → LIGHT, Balanced and Custom → BALANCED, One per worktree → PER_WORKTREE, Automatic → BALANCED with 16 GB or more and LIGHT otherwise, and LIGHT while `PowerSource` says the machine is on battery. It is a separate use case rather than a method of `ResolveResourceLimitsUseCase` because it has a different port (power) and a different consumer (only the desktop app); both share `MachineCapacity`. Nothing is stored: the settings file is unchanged.

The board and the machine views receive a `() -> RefreshCadence` and call it before each wait, so a profile change in Preferences applies from the next wait without new wiring through `ResourcePreferences`.

The starting values are the ones in the spec and are tuned with the benchmark before archiving; a tuning that changes them updates the spec in this change.

### 5. Tying the cadence to the capacity profiles is provisional

Decided by Ciro: the cadence follows the capacity profiles instead of being a setting of its own, so this change adds no new choice to Preferences. The profiles were made for device and daemon limits, so "Light" meaning both "one emulator" and "refresh every 10 s" is a shortcut: a person on a big machine who wants few emulators but a fast board can't have both. This is accepted for now and stated as provisional here and in the profiles' documentation (`docs/guide/providers.md` and `.es.md`). The "Rethink Settings" roadmap entry added by this change carries revisiting it.

### 6. Window activity with three states

`main.kt`/`AppShell` derive a `WindowActivity` (`FOCUSED`, `UNFOCUSED`, `HIDDEN`) from `windowShown` and `windowInfo.isWindowFocused` and push it to `WorktreeBoard` and `MachineResources` through `setWindowActivity`, replacing `setWindowActive(Boolean)`. Their loops wait with `withTimeoutOrNull(interval) { activity.drop(1).first() }`, so a change of state ends the current wait and the next interval is computed for the new state (the pattern `runSummaryTicks` already uses). `HIDDEN` waits until the state changes, as today. Focus regained still runs `refreshOnFocus`. The shown machine view waits `max(machineView, unfocusedCheck)` when unfocused; summaries use `summaries` in both visible states.

An unreported focus counts as focused, as an unreported visibility counts as visible today. A Preferences or About window of ShogunAi taking focus leaves the main window unfocused: accepted, the slower pace applies only while that window is in front.

### 7. Power source: `pmset` on macOS, nothing elsewhere

`PowerSource` (core `domain/io`) has `suspend fun isOnBattery(): Boolean?`. `PmsetPowerSource` (infrastructure) runs `pmset -g batt` and reads `'Battery Power'` or `'AC Power'` on its first line; any other output, a failure or a timeout gives `null`. Windows and Linux get `PowerSource.UNKNOWN` (always `null`), and `null` counts as AC. The resolver caches the answer for 60 s and only asks when the profile is Automatic, and it is only asked from the loops, which don't run while the window is hidden, so a hidden app never runs `pmset`.

Alternatives: IOKit through JNA (a new dependency for one boolean), `/sys/class/power_supply` on Linux (cheap, but not asked for; easy to add later behind the same port).

### 8. `shogun status` reads concurrently

`ShogunCli.status` runs the main branch, agent detection, base branches and recorded bases in parallel with `async`, then the per-worktree statuses with `async` bounded by a `Semaphore` of 8, collected with `awaitAll` in listing order, so output is identical. The executor is already suspending; the reads run on `Dispatchers.IO`. Status reads use `--no-optional-locks`, so concurrent reads don't contend for the index lock. Multirepo `groupStatus` gets the same treatment per repository if it shares the code path; otherwise it is left as is.

### 9. Benchmark: counting tests in every build, a script for real numbers

- Counting tests (run by `./gradlew test`): a core test that `DetectRunningAgentsUseCase` runs no `lsof` without candidates and restricts it to candidate pids; a core test that repeated `directoriesOf` on an unchanged worktree runs `git rev-parse` once; a desktop test that a focused check of a project with N worktrees and unchanged metadata starts no `git rev-parse` and no worktree listing, and that an index-only change reads statuses without listing; a desktop test of the cadence per activity with a virtual clock; a CLI test that statuses are read with more than one in flight and printed in order.
- A script, `scripts/benchmark-idle.sh`, for the real cost: it creates a temporary repository with N worktrees (default 10), times `shogun status` from the CLI's install distribution K times (median), then runs the desktop app (`:desktopApp:run`) with an isolated `user.home` registering that repository and a JFR recording enabled by a Gradle property, for a given number of seconds, and prints the processes started per minute and the most frequent commands from the recording's `jdk.ProcessStart` events. Whether the window is focused during the run is up to the person running it; the script says which state to leave it in. Documented in `docs/process/benchmark.md` (+ `.es.md`) with the baseline measured before and after this change.

A script rather than a Gradle task because it needs a real desktop session and minutes of wall time, which CI can't give; the counting tests are what guards regressions automatically.

### 10. Roadmap placement

Both new entries go under "Under exploration": neither is ready to become a change, since each first needs deciding what the app shows by default (UX) and which few choices Settings offers at the top level. The UX entry comes first because the Settings rethink depends on what the UX keeps on screen. "Filesystem watchers instead of polling for Git metadata changes" stays in "6. Robustness and maintenance".

## Risks / Trade-offs

- [Unfocused windows update slower: an agent starting in a worktree on a second monitor shows up after up to 15 s on Balanced instead of 3 s] → One per worktree keeps 5 s for those who watch the board in the background; focusing the window refreshes right away.
- [An agent whose executable isn't named like a known agent (e.g. run through `node`) isn't detected] → Same as today: today's filter is by name as well.
- [`.git` validation misses a worktree whose admin directory changed while its `.git` file content stayed identical] → Git writes the admin directory into that file, so the content changes with it; `git worktree repair` or a move rewrites it too.
- [Index-only detection treats a change in `index` that came with a branch switch as index-only if the `HEAD` stamp didn't change] → A branch switch always rewrites `HEAD`, so it triggers a full reload.
- [Concurrent `status` adds load spikes] → Bounded to 8 reads; total work is the same, only spread over cores.
- [`pmset` output differs between macOS versions] → Unknown output counts as AC, the cadence then follows memory, as on other systems.

## Migration Plan

No data migration: the cadence is derived from the stored profile. Rollback is reverting the change.
