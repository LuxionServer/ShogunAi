# Proposal

## Why

Today every worktree of a repository that declares the emulator or the iOS simulator provider gets its device when its environment is first resolved, that is, when the worktree is created. Worktrees that never run the app still use up one of the 16 emulator console ports and a simulator's disk, and nothing bounds how many emulators, simulators or Gradle daemons run at once, so a modest machine runs out of memory while a powerful one has no reason to be limited. Each provider solves capacity on its own, or not at all.

## What Changes

- **BREAKING**: a worktree acquires its emulator (console port and `ANDROID_SERIAL`) and its iOS simulator (`IOS_SIMULATOR_UDID`) when a device is first started for it, from the app or with `shogun emulator start` / `shogun simulator start`, not when its environment is first resolved. Creating a worktree, `shogun env` and the `SessionStart` hook no longer reserve a port nor create a simulator; they deliver the variable once the worktree holds the device.
- A shared resource pool model: emulators, iOS simulators and Gradle daemons are resources of limited capacity that a worktree acquires and releases, with one limit per kind. Emulators and simulators are acquired by starting them; Gradle daemons, which agents start themselves, are kept within their limit by stopping idle ones.
- When a pool is full, acquiring releases the least-recently-used idle resource of that kind (a stopped emulator's reservation, a shut-down simulator, the longest-idle Gradle daemon). If none is idle, it fails with an error that names which worktree holds each resource.
- Machine-level capacity profiles in Preferences, stored in the app settings and never in the repository: Automatic (default, derived from the machine's memory and cores), Light, Balanced, One per worktree (no limit beyond what the platform imposes) and Custom (a number per resource kind). The repository keeps declaring only what it needs.
- The machine views show each pool's usage against its limit and say when a resource was released to make room.
- The session-start context tells the agent how to get its device when the worktree doesn't hold one yet.
- DerivedData stays out of this change: its disk quota is a follow-up that will reuse the pool model.

## Capabilities

### New Capabilities

- `resource-pools`: limited-capacity pools of machine resources (emulators, iOS simulators, Gradle daemons), the capacity profiles and their limits, acquiring and releasing, and what happens when a pool is full.

### Modified Capabilities

- `emulator-provider`: the console port and `ANDROID_SERIAL` are reserved when the emulator is first started, through the emulator pool, instead of when the environment is resolved.
- `ios-simulator-provider`: the simulator is created when it's first started, through the simulator pool, instead of when the environment is resolved; a released simulator is deleted.
- `gradle-provider`: idle Gradle daemons are stopped to keep their number within the Gradle daemon pool's limit.
- `worktree-environment`: providers may contribute reservations that are acquired on demand and only read when the environment is resolved.
- `command-line-tool`: `shogun emulator start` and `shogun simulator start` acquire the device through its pool; `shogun env` and the `SessionStart` hook no longer acquire devices and tell the agent how to; a new `shogun pool` command shows and trims the pools.
- `app-shell`: Preferences gains a "Resources" section with the capacity profile and, for Custom, a limit per resource kind.
- `machine-resources`: each machine view shows its pool's usage against the limit and the resources released to make room.

## Impact

- Core: a new `ResourcePool` domain model and use cases (acquire, release, trim, usage), a `ResourceLimits` setting with a repository over `JsonSettingsFile`, and a `MachineCapacity` port to read memory and cores. `ResolveWorktreeEnvironmentUseCase` and `EnvironmentContributions` gain on-demand reservations; `StartWorktreeEmulatorUseCase` and the simulator start use case acquire through the pool; `StopGradleDaemonsUseCase` is reused for trimming.
- CLI: the CLI reads the app settings file for the limits; `emulator start`, `simulator start`, `env`, `hook session-start` change; `pool status` and `pool trim` are added.
- Desktop app: Preferences "Resources" section; machine views show usage and releases; the periodic machine refresh trims the Gradle pool.
- Docs: `docs/architecture/` (domain, CLI, UI) in English and Spanish, the README's provider section and the roadmap (item 2 removed, DerivedData quota added as a follow-up).
- Existing worktrees keep the devices they already hold: a stored port or simulator UDID counts as acquired, so nothing is lost on upgrade.
