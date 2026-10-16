# Design

## Context

See proposal.md for the motivation. Facts that shape the approach:

- KGP 2.4.10's `KotlinNativeSimulatorTest` has `device: Property<String>` (the older `deviceId` is deprecated) and `standalone: Property<Boolean>`; it runs tests with `xcrun simctl spawn <device> …` and, when nothing is set, takes a default from `xcrun simctl list devices available` (`XcodeDefaultTestDevicesValueSource`). It reads no environment variable, so exporting a UDID is not enough: something has to set `device`.
- `xcodebuild` takes the simulator as `-destination 'id=<UDID>'`, so agents pass the variable themselves; the `SessionStart` context is where they learn it.
- `simctl create <name> <device type id> <runtime id>` makes a fresh device (17 MB; 364 MB after an 18 s first boot; 3 s boots afterwards). `simctl clone` copies a device's data (2.6 GB for a used one). Devices must live in the default device set, since `xcodebuild` and KMP's `simctl spawn` only see that one.
- #72 added `EnvironmentContribution`s (port requests and variables) to `ResolveWorktreeEnvironmentUseCase`, a machine-wide reservation index and lock, and the pattern of a provider port (`AndroidSdk`), use cases, CLI commands, a row line, a cleanup option and a configuration section, all gated by the provider's declaration.

## Goals / Non-Goals

**Goals:**
- One simulator per worktree, created cheaply, recreated if lost, deleted with the worktree, never touching simulators ShogunAi didn't create.
- The UDID available in the environment from the start, so agents don't need an extra step.
- KMP tests wired by the repository itself, with a machine-wide fallback only after explicit consent.

**Non-Goals:**
- Physical iOS devices, watchOS/tvOS/visionOS simulators, Xcode's DerivedData (step 9e), CocoaPods or SPM caches.
- Running `xcodebuild` or the tests for the agent.

## Decisions

### Resources next to ports in the reservations
`EnvironmentContribution` gains `resources: List<ResourceRequest>` (`key`, `exists: suspend (String) -> Boolean`, `create: suspend (ResourceContext) -> String`, where `ResourceContext` has the repository and worktree folder names) and its `variables` receive resources as well as ports. `EnvironmentStateStore` moves from ports-only to `WorktreeReservations(ports, resources)` (`read`/`write`), stored as `{ "ports": {…}, "resources": { "ios-simulator": "<UDID>" } }`; the machine index records an administrative directory while it has either, and `recordedResourcesOnMachine(key)` lists every recorded value (for orphans). Creating a simulator takes about a second and runs `xcrun`, so it isn't done under the machine lock: the use case checks the stored value with `exists`, creates outside the lock when needed, then writes under the lock; if another process recorded a live resource meanwhile, it deletes its own and keeps the recorded one. With `reserve = false` resources are only read.
Alternative: a separate store per provider. The reservations file already disappears with the worktree, which is exactly the lifetime wanted.

### Contributions built with machine context
`providerContributions` becomes a `ProviderContributions` factory injected into `ResolveWorktreeEnvironmentUseCase`, built by the app and the CLI with what providers need: the `AppleSimulators` port, the project's machine settings for a repository (looked up in the project catalog) and whether the host is macOS. The emulator contribution moves into it unchanged; the iOS one exists only on macOS with the provider declared, and exports `IOS_SIMULATOR_UDID`.

### Declaration, settings and choice
`RepositoryProviders.iosSimulator: IosSimulatorProvider?` (`device`, `runtime` preferences) is parsed like `emulator`. `ProjectConfig.simulator: SimulatorSettings(device, runtime)`. `chooseSimulator(requested, settings, preferred, available)` is pure: runtime by display name (`iOS 18.5`), case-insensitive, each level used only if installed; default runtime is the newest iOS one by version; device type by name among the runtime's `supportedDeviceTypes`, default the last iPhone in that list (Apple lists newer types later). No iOS runtime is `NoIosRuntime`.

### One port for Apple's simulators
`AppleSimulators` in `domain/io`: `isSupported()` (macOS), `runtimes()` and `deviceTypes()`, `devices()` (UDID, name, state), `create(name, deviceTypeId, runtimeId)`, `boot(udid)`, `waitUntilBooted(udid, timeout)`, `shutdown(udid)`, `delete(udid)`. `SimctlAppleSimulators` runs `xcrun simctl list -j runtimes|devicetypes|devices`, `create`, `boot`, `bootstatus <udid> -b` (bounded by the timeout), `shutdown`, `delete` through `ShellCommandExecutor`. Booting an already booted device and shutting down a shut-down one are treated as success (simctl's "current state" errors).

### Use cases
`StartWorktreeSimulatorUseCase` (resolves with reservation, so the simulator exists, then boots and waits, 120 s), `StopWorktreeSimulatorUseCase`, `ReadSimulatorStatesUseCase` (recorded UDIDs without creating, one `devices()` call), `DeleteSimulatorsUseCase` (given UDIDs plus orphans: names starting with `ShogunAi · ` whose UDID isn't in `recordedResourcesOnMachine`). All return early or fail with `IosSimulatorProviderNotDeclared` like the emulator ones.

### KMP tests wiring
`KmpTestWiring` port: `isWiredInRepository(repositoryPath)` scans `*.gradle.kts` and `*.gradle` up to four levels deep (skipping `build`, `.gradle`, `.git`, `node_modules`) for `IOS_SIMULATOR_UDID`; `isInitScriptInstalled()`, `installInitScript()`, `uninstallInitScript()` manage `$GRADLE_USER_HOME/init.d/shogunai-ios-simulator.gradle`. The init script is Groovy, so it can set the property without KGP on its classpath:

```groovy
def udid = System.getenv("IOS_SIMULATOR_UDID")
if (udid) {
    allprojects {
        tasks.configureEach { task ->
            if (task.class.name.startsWith("org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest")) {
                task.device.set(udid)
            }
        }
    }
}
```

The repository snippet ShogunAi shows:

```kotlin
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    providers.environmentVariable("IOS_SIMULATOR_UDID").orNull?.let { device.set(it) }
}
```

Both are no-ops without the variable. `simulatorTestsWired = repository wired || init script installed`.

### CLI, desktop and cleanup
Same shape as the emulator: `shogun simulator start|stop|status`, `shogun simulator gradle-init install|uninstall [--yes]` (prints the path and content, reads a `y` answer from standard input), `shogun clean --delete-simulators`; a `SimulatorLine` under the emulator line; the cleanup dialog option (checked) lists orphans and enables "Clean up" when only orphans exist; an "iOS simulator" configuration section with two dropdowns and the wiring panel (copy snippet; install with a confirmation dialog). UDIDs of removed worktrees are read before removal, since removal deletes their reservations. The `SessionStart` context adds: `Its iOS simulator is IOS_SIMULATOR_UDID=<udid>; boot it with 'shogun simulator start' and build with xcodebuild -destination "id=$IOS_SIMULATOR_UDID"`.

## Risks / Trade-offs

- [Each worktree's simulator costs ~360 MB once booted] → Deleted with the worktree; orphans are found by name and recorded UDIDs.
- [A user renames a ShogunAi simulator] → It stops matching the prefix and is never deleted by ShogunAi; its worktree recreates one only if the UDID is gone.
- [Two processes resolving the same worktree at once could both create a simulator] → The loser deletes its own after re-reading under the lock.
- [The init script affects every Gradle build on the machine] → Only after explicit confirmation, only when `IOS_SIMULATOR_UDID` is set, removable with one command.
- [Simulator runtime names change between Xcode versions] → Matching uses `simctl`'s own names and identifiers, never hard-coded ones.

## Migration Plan

No migration: the state file gains an optional `resources` object; files without it load as before. `projects.json` without `simulator` loads with defaults.
