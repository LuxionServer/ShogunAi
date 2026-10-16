# Design

## Context

See proposal.md for the motivation. Facts that shape the approach:

- AGP 9.0.1: `InstallVariantTask`, `UninstallTask` and `DeviceProviderInstrumentTestTask` build `ConnectedDeviceProvider(adb, timeout, logger, System.getenv("ANDROID_SERIAL"))`, which splits the value on `,`, keeps only the devices with those serials, and fails with "Connected device with serial '…' not found!" otherwise. Without the variable every connected device is used. The lookup runs in the Gradle daemon, which applies the client's environment variables for each build.
- An emulator started with `-port <P>` (even, 5554–5584) listens on console port P and adb port P+1 and is named `emulator-<P>`; `adb` only discovers emulators in that range, so 16 can run at once. The same AVD can run several times only with `-read-only`.
- #71 resolves a worktree's environment in `ResolveWorktreeEnvironmentUseCase`, reserving ports in Git's administrative directory under a per-repository lock, and delivers it through `shogun env`, the `SessionStart` hook and `envFile`. Port uniqueness was per repository.
- `ProjectConfig` is serialized with defaults to `~/.shogunai/projects.json`, so a new optional field loads in older files.

## Goals / Non-Goals

**Goals:**
- Zero effect on repositories without the provider: no SDK lookup, no `adb` call, no UI, no variable.
- Configuration at the right level: what the repository needs in `.shogunai.json` (the provider, a preferred AVD), what depends on the machine in the project (AVD, window), what a single run needs on the command line.
- Providers plug into the environment without the environment knowing about Android.

**Non-Goals:**
- Physical devices, iOS simulators (a later provider can reuse the same contribution point), creating, cloning or wiping AVDs.
- Starting emulators automatically when a worktree is created or a session starts.
- Per-worktree Gradle settings.

## Decisions

### Providers contribute port requests and variables
`ResolveWorktreeEnvironmentUseCase` gathers `EnvironmentContribution`s from the declared providers: each has `PortRequest`s (`key`, candidate ports, extra ports that must also be free) and a function from its reserved ports to variables. The emulator contribution requests key `emulator` from `5554, 5556, …, 5584` with `port + 1` also free, and yields `ANDROID_SERIAL=emulator-<port>`. Declared `env` variables are rendered after and override provider variables with the same name. Variable ports keep their 20000–29999 range as a `PortRequest` too, so one loop reserves everything.
Alternative: a special `${emulator}` placeholder users must write; it would make the common case (just set `ANDROID_SERIAL`) manual and error-prone.

### Machine-wide uniqueness with a self-healing index
`EnvironmentStateStore` gains a machine index (`~/.shogunai/reservations.json`, the administrative directories that hold reservations). `reservedPortsOnMachine()` reads the current repository's directories plus every indexed one, dropping entries whose state file no longer exists (the worktree was removed or pruned). Writing a worktree's ports adds its directory to the index. The lock moves from `<common-dir>/shogunai-env.lock` to `~/.shogunai/reservations.lock`, because uniqueness now spans repositories.
Alternative: keep per-repository uniqueness. Two repositories would then both reserve 5554 when neither emulator runs, and the second start would fail.

### Provider declaration and machine settings
`RepositoryProviders` gains `emulator: EmulatorProvider?` (`preferredAvd: String?`), parsed like `gradle`. `ProjectConfig` gains `emulator: EmulatorSettings = EmulatorSettings()` (`avd: String?`, `noWindow: Boolean`). `ChooseAvd(requested, settings, preferred, available)` is a pure function implementing the order in the spec.

### One port for the Android SDK
`AndroidSdk` in `domain/io`:

```kotlin
interface AndroidSdk {
    fun locate(repositoryPath: String): SdkLocation          // throws AndroidSdkNotFound(checked)
    suspend fun listAvds(sdk: SdkLocation): List<String>
    suspend fun deviceStates(sdk: SdkLocation): Map<String, EmulatorState>
    fun startEmulator(sdk: SdkLocation, avd: String, port: Int, noWindow: Boolean, logFile: String)
    suspend fun stopEmulator(sdk: SdkLocation, serial: String)
}
```

`LocalAndroidSdk` resolves the SDK in the spec's order (reading `local.properties` with `FileManager.readProperties`), runs `emulator -list-avds`, `adb devices` and `adb -s <serial> shell getprop sys.boot_completed` (only for emulators listed as `device`) and `adb -s <serial> emu kill` through `ShellCommandExecutor`, adding `.exe` on Windows. Starting uses `ProcessBuilder` directly, not the executor, because the process must outlive the caller: `emulator -avd <avd> -port <P> -read-only -no-boot-anim [-no-window]`, output redirected to `emulator.log` in the worktree's administrative directory, not waited for. `deviceStates` makes one `adb devices` call for all worktrees.

### Emulator use cases
`StartWorktreeEmulatorUseCase` resolves the environment (reserving the port if needed), returns early when `deviceStates` already shows the serial running, chooses the AVD, starts it and polls `deviceStates` every 2 s until running or 180 s pass (`EmulatorBootTimeout`, emulator left running). `StopWorktreeEmulatorUseCase` and `ReadEmulatorStatesUseCase` (serials of the listed worktrees from stored reservations, read-only, plus one `deviceStates`) complete the set. The poll delay is injected so tests don't wait.

### Cleanup
Removing a worktree deletes its administrative directory and its reservations, so the desktop view model and `shogun clean` read the serials of the candidates before removing, then stop the ones still running after the removals, like Gradle daemons. Results join the cleanup report, never its failures.

### Desktop
`WorktreeViewModel` loads emulator states only when the repository declares the provider, in the overview load and on each auto-refresh tick without changes (one `adb devices`), and exposes `startEmulator(worktree)` / `stopEmulator(worktree)` with a per-worktree "starting" flag. The row adds a line: `Emulator · running` with Stop, or `stopped` with Start. `ProjectConfigViewModel` lists AVDs only when the repository declares the provider and shows "SDK not found" with the checked locations otherwise.

### CLI and session context
`CliCommand.Emulator(action, path, avd, noWindow)` and `Clean.stopEmulators`. `hook session-start` adds `; start its emulator (ANDROID_SERIAL=…) with: shogun emulator start` to the context line when the provider is declared.

## Risks / Trade-offs

- [`-read-only` discards the emulator's data on exit] → Agents reinstall on each run anyway; documented. A writable mode for a single worktree can come later.
- [An emulator started outside ShogunAi (Android Studio's 5554) occupies a port] → Both ports are probed before reserving, so a running one is skipped; a stopped one started later on a reserved port is the user's choice.
- [The detached emulator keeps running after the CLI exits] → Intended; Stop, cleanup and the row stop it.
- [Boot time varies (cold boot without snapshots in read-only)] → 180 s timeout and the emulator keeps booting after a timeout; status shows "booting".
- [Machine index file grows with stale entries] → Every read drops entries whose state file is gone and rewrites the index under the lock.
- [16 emulator ports] → Enough for parallel agents in practice; exhausting them is a clear error.

## Migration Plan

No migration. Existing reservations are indexed the next time they're written; until then they still count for their own repository, as before. `projects.json` files without `emulator` load with defaults.
