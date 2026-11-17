# Design

## Context

The pieces this change builds on:

- `WaitInLine` (from the desktop-turn change) holds the FIFO wait loop shared by devices and the desktop turn: tickets in `~/.shogunai/device-queue.json` keyed by `WaitQueueName`, dropped when their `ProcessIdentity` no longer runs (`ProcessLiveness`), the queue-jump check `othersAheadOf`, progress on every poll and ticket removal in `NonCancellable`.
- `DesktopTurnLedger` and its use cases show how a machine-level resource lives in its own file under `~/.shogunai`, written under `EnvironmentStateStore.withLock`.
- `ResolveResourceLimitsUseCase` turns the capacity profile and `MachineCapacity` into limits, and is used by the app and the CLI alike.
- `shogun hook desktop-turn` and its `DesiredHook` entries show how a `PreToolUse` hook denies a command and tells the agent what to run instead; `OrcaComputerUse` shows how a shell command is split into segments and words.
- `CliCommand.kt`, `CommandJson.kt`, `ShogunCli.kt` and `WorktreeError.kt` are at or near their file length caps and nine other changes edit them in parallel.

## Goals / Non-Goals

**Goals:**

- Reuse the existing wait queue and loop instead of writing another.
- A slot can never stay taken after its build ended, whatever happened to it.
- Agents meet the turn when they start a build, without having read the guide.
- Touch shared files only with dispatch lines; everything else in new files.

**Non-Goals:**

- Enforcing the turn for builds started outside Claude Code's hooks (a terminal, an IDE, CI). It is cooperative, like the desktop turn.
- Bounding the memory or the parallelism of a build itself (the Gradle provider already bounds daemons).
- The desktop app: showing the build turn in the control tower and a Custom field in Preferences are a follow-up.
- Weighing builds by size (a `help` task and a full `test koverVerify` take the same slot when they aren't light invocations).

## Decisions

### Only a wrapper, slots held by the build's process

`shogun build run [--wait-timeout <minutes>] -- <command…>` waits for a slot, runs the command, frees the slot and exits with the command's code. There is no `acquire`/`release` pair. Decided with Ciro: a pair needs an expiry, and a build has no good expiry (a full test run can last 40 minutes, a crash shouldn't block others for that long), while a wrapper knows exactly when the build ends.

A slot records the `ProcessIdentity` of the `shogun` process (pid and start time, as wait tickets do). Reading the slots drops those whose process no longer runs, so `kill -9`, a crash or a reboot free the slot on the next read without expiry or cleanup. The wrapper also removes its slot in a `finally` under `NonCancellable`, so a normal end frees it at once.

Alternative considered: hold the slot with the child's pid. Rejected: the child is started after the slot is taken, and `shogun` stays alive for the build's whole life anyway.

### Machine-level slots file, not a `ResourceKind`

The slots live in `~/.shogunai/build-turns.json` (a list of `{ id, adminDirectory, worktreePath, command, since, pid, processStartedAt }`) behind a new port `BuildTurnStore` with an NIO implementation next to `NioDesktopTurnStore`, written atomically under `EnvironmentStateStore.withLock`. Like the desktop turn, builds aren't a `ResourceKind`: they have no victims to release and adding a kind leaks into every `when` over pools, `shogun pool status` and the app's pool views (and collides with the parallel `custom-pool-provider` change).

The waiters use the existing queue file with a new `WaitQueueName.BUILD` (`"build"`), an additive enum entry: existing keys keep their format and old versions ignore the new key.

### Reuse `WaitInLine`

`AcquireBuildSlotUseCase` calls `WaitInLine.await` with an `attempt` that, under the lock, reads the live slots and the live queue and either records a new slot or throws `BuildTurnError.SlotsTaken(limit, running, waiting)`; the queue-jump check is the shared `othersAheadOf`. The wait is always on (there is no `--wait` flag: a wrapper that fails when busy would just make the agent retry), bounded by `DEFAULT_DEVICE_WAIT_MINUTES` (30) or `--wait-timeout`. `WaitInLine` itself is not modified.

`othersAheadOf` compares worktrees, so a second build of a worktree that already waits doesn't queue behind its own ticket. That matches devices and the desktop and is harmless: both builds still need a free slot.

### Typed errors outside `WorktreeError`

The build turn's errors are a separate sealed class `BuildTurnError` (`SlotsTaken`, `WaitTimedOut`) in its own file, not `WorktreeError` subclasses. Every `WorktreeError` must have English and Spanish texts in the desktop app (`EveryErrorTest`), in files at their length caps, for errors the app can't produce since it never runs builds. Use cases still return `Result<T>` with these typed errors. When the app follow-up shows the build turn, moving them under `WorktreeError` with their texts is part of it.

### Limits

`ResolveResourceLimitsUseCase.buildSlots(): Int?` (one method, additive) delegates to a function in a new file: Light 1, Balanced 2, One per worktree `null` (no limit), Automatic `min(memoryGB / 16, cores / 4)` clamped to 1..4, or 1 when memory can't be read, and Custom uses the Automatic value. `ResourceLimits` gets no new field: Custom has no Preferences field yet, and an absent field in stored Custom settings would read as "no limit". The app follow-up adds the field. With no limit, slots are still recorded, so `shogun build status` and `shogun status` show running builds.

The Automatic figure assumes a heavy build needs about 16 GB and 4 cores to run without swapping next to the IDE and the agents (Gradle daemon heap, Kotlin daemon and test workers), double the 8 GB per daemon used for the daemon pool. Decided with Ciro.

### Running the command

A new port `ForegroundCommandRunner.run(command: List<String>, workingDirectory: String): Int?`, part of `EnginePorts`, with `ProcessBuilderForegroundCommandRunner` (`inheritIO()`, waits for the process interruptibly, returns its exit code, `null` when it can't be started, and destroys the process when the run is cancelled). The command is argv as received after `--`; no shell parses it. `shogun build run` exits with 127 when it couldn't start, like a shell.

Ctrl+C reaches the build and the `shogun` JVM together (same process group); the JVM exits through its shutdown, and the slot or the ticket left behind is dropped on the next read because the process is gone.

### The hook

`shogun hook build-turn` is a `PreToolUse` hook for `Bash`, installed as four `DesiredHook` entries with the conditions `Bash(*gradle*)`, `Bash(*mvn*)`, `Bash(*xcodebuild*)` and `Bash(*swift *)`, so other commands don't start a JVM. `HeavyBuildCommand` (domain model, new file, modeled on `OrcaComputerUse`) splits the command into segments on `;`, `&`, `|`, `(`, `)`, newlines and backticks, skips leading environment assignments and `env`, `time`, `nice`, `nohup`, `command` and `exec`, and flags a segment whose executable's base name (without `.exe`, `.cmd`, `.bat`) is `gradle`, `gradlew`, `mvn`, `mvnw` or `xcodebuild`, or `swift` followed by `build` or `test`, unless it is a light invocation. A segment that starts with `shogun build run` is never flagged, so the wrapped build runs.

Decided with Ciro: the hook denies (it doesn't rewrite the command with `updatedInput`), because a rewritten command would wait inside the agent's shell call and hit its timeout without the agent knowing why. The denial quotes the flagged segment prefixed with `shogun build run --` and says to run it in the background or with a long timeout. It always denies an unwrapped heavy build when a limit exists, even with free slots, since a build outside the turn isn't counted. It is active in every repository (no `.shogunai.json` opt-in), as Ciro chose; with the One per worktree profile it lets every build run.

### Agents without hooks

The `resources` topic of `shogun guide` gets `resources.build-turn`, without condition and not in the session-start context (as the desktop-turn rule), since the hook teaches it when it matters and session-start lines are budgeted.

### Status

`shogun build status [--json]` reads the slots, the queue and the limit once. `shogun status` reads the build state once per run, next to the desktop turn, and adds `build(running N)` / `build(waiting P/T)` to a worktree's line and a `build` field to its JSON object (`null` when it neither runs nor waits), additive per the JSON stability rule. The worktree is matched by its path, as the desktop note does.

### CLI files

Parsing (`parseBuild`), the command class, texts and JSON live in new files `BuildCommand.kt` and `BuildTurnText.kt`; the hook and the command handlers live in a new `BuildTurnCommands` class that `ShogunCli` delegates to. Shared files get only dispatch lines: the `when` branches in `parseArguments`, `parseHook`, `ShogunCli.execute`, `HOOK_EVENTS`, the help entry, the status read and note. `UsageException`, `DeviceWaitOptions` and `valueOf` in `CliCommand.kt` become `internal` so the new parser can use them, and `build` stays out of the `--json` commands list since only `build status` takes it and `build run` passes everything after `--` to the build. `CliCommand.kt` gains two lines over its cap of 1090 (the `"build"` and `"build-turn"` dispatch branches): its cap rises to 1092, or to 1093 on top of the 1091 another change set while this one was open, since splitting the file is a refactor outside this change's scope and would collide with the nine parallel changes editing it. `ShogunCli.kt` stays under its cap.

## Risks / Trade-offs

- [Agents can still build outside the turn, e.g. a build started by a script] → Cooperative by design; the hook covers direct commands, which are what agents run.
- [A command that mentions `gradle` only as an argument, e.g. `echo gradle`] → The executable, not any word, decides; the condition only decides whether the hook starts.
- [The hook denies a light Gradle task not in the light list, e.g. `./gradlew dependencies`] → The agent reruns it wrapped; with free slots it runs at once. The light list can grow.
- [Waiting inside the agent's shell call] → The denial and the guide say to run it in the background or with a long timeout; progress goes to standard error.
- [Pid reuse after a reboot] → The identity includes the process start time, as wait tickets do.
- [A JVM per heavy build for the hook] → Only commands matching the conditions start it, and builds already take far longer.

## Migration Plan

No migration: a missing `build-turns.json` means no running builds, the `build` queue key starts empty, and installed hooks are reported outdated until `shogun hooks install` adds the new entries. Rolling back ignores the file and the key.
