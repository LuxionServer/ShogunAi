# Providers

A provider adds support for one kind of tooling to a repository's worktrees. A repository turns providers on in its [`.shogunai.json`](configuration.md#repository-configuration-shogunaijson) under `providers`; repositories without a provider never see any of its sections, buttons or commands, and none of its tools run.

```json
{ "providers": { "gradle": {}, "emulator": {}, "ios-simulator": {}, "xcode": {} } }
```

| Provider | For | Platforms |
|---|---|---|
| [`gradle`](#gradle) | Android, KMP and JVM repositories: Gradle daemons and their memory. | Any; the daemon list needs `ps` (macOS, Linux) |
| [`emulator`](#android-emulator) | An Android emulator per worktree. | Any with the Android SDK |
| [`ios-simulator`](#ios-simulator) | An iOS simulator per worktree. | macOS |
| [`xcode`](#xcode) | Xcode DerivedData and the shared compilation cache. | macOS |

## Gradle

```json
{ "copy": ["/local.properties"], "providers": { "gradle": {} } }
```

Every build in a worktree starts a Gradle daemon (and usually a Kotlin compile daemon) when no idle one can be reused, and each daemon keeps its memory for hours after the build. For a Gradle repository, the worktree screen shows a "Gradle daemons" section with every daemon on the machine: its memory, whether it's busy or idle, and the worktree of its last build. You can stop idle daemons there, and "Clean up" offers to stop the idle daemons of the worktrees it removes. A busy daemon is never stopped.

If the repository doesn't turn on Gradle's build cache (`org.gradle.caching=true` in `gradle.properties`), ShogunAi tells you: with it on, a new worktree's first build reuses what other worktrees already built.

```bash
shogun gradle                      # list the daemons, their memory, state and worktree
shogun gradle stop                 # stop every idle daemon (or only the given process ids)
shogun clean --yes --stop-gradle-daemons  # also stop the idle daemons of the removed worktrees
```

Daemon inspection needs `ps` (macOS and Linux).

### Daemon memory and lifetime per worktree

Parallel builds can't share a busy daemon, so three agents building at once start three daemons, each with the heap your `gradle.properties` asks for, and they stay alive for three hours. The provider can bound the daemons that worktree builds start:

```json
{ "providers": { "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30, "kotlinDaemonMaxHeap": "1g" } } }
```

Every setting is optional. Memory sizes are a number followed by `k`, `m` or `g`; the timeout is in minutes. With at least one, every worktree's environment (the main one included) gets the same `GRADLE_OPTS`, which overrides `gradle.properties` for builds started from that shell. `daemonMaxHeap` only replaces `-Xmx`: the other `org.gradle.jvmargs` of your user `gradle.properties`, or else the repository's, are kept. The "Gradle daemons" section shows the settings in effect.

- It reaches agents like any [per-worktree variable](../agents.md#per-worktree-environment). A `GRADLE_OPTS` you set yourself in that shell is replaced; declare it in `env` instead and it takes precedence over the provider's.
- Builds started from Android Studio or IntelliJ don't inherit the agent's shell: they keep using `gradle.properties` and don't share daemons with agent builds. Put the limits in `gradle.properties` too if you want them everywhere.
- New settings apply to new daemons; idle daemons started with other arguments aren't reused and time out on their own, or stop them from the section.
- The quoting is designed for Windows' `gradlew.bat` too, but has only been verified on macOS.

## Android emulator

Android builds install on every connected device unless `ANDROID_SERIAL` names one, so agents in parallel worktrees step on each other's emulators. Turn on the emulator provider to give each worktree its own:

```json
{ "providers": { "emulator": { "avd": "Pixel_8_API_35" } } }
```

`avd` is optional: the AVD this repository prefers when the machine has it. A worktree reserves an emulator port, and gets `ANDROID_SERIAL=emulator-<port>` in its environment (`shogun env`, the `SessionStart` hook, `envFile`), the first time you start its emulator, not when it is created; `shogun emulator start` prints the `export ANDROID_SERIAL=…` line to run in a shell that started before. Emulators only start when you ask:

```bash
shogun emulator start      # start this worktree's emulator (read-only, so one AVD serves every worktree) and wait for it
shogun emulator status     # emulator-5556 running
shogun emulator stop
```

- On each machine, the project configuration chooses the AVD (or leaves it automatic) and whether it runs without a window; `--avd` and `--no-window` override them for one start.
- The worktree list shows each emulator with Start and Stop, and "Clean up" (or `shogun clean --stop-emulators`) stops the emulators of the worktrees it removes.
- The SDK is found through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `sdk.dir` in `local.properties` or Android Studio's default location.
- The adb server is shared by every worktree: agents must never restart it (`adb kill-server`) or force a device with `-s`, `-e` or `-d`. The `SessionStart` hook tells Claude so.

## iOS simulator

Parallel agents on a KMP or iOS project would otherwise share the same simulators. Turn on the iOS simulator provider (macOS only) to give each worktree its own:

```json
{ "providers": { "ios-simulator": { "device": "iPhone 16", "runtime": "iOS 18.5" } } }
```

Both options are optional preferences used when the machine has them. A worktree gets a new simulator (created, never cloned: about 17 MB, 360 MB once booted) named `ShogunAi · <repository> · <worktree>`, and `IOS_SIMULATOR_UDID` in its environment, the first time you start it. Build for it with `xcodebuild -destination "id=$IOS_SIMULATOR_UDID"`.

```bash
shogun simulator start     # create if needed, boot this worktree's simulator and open it (Simulator, or Device Hub in Xcode 27)
shogun simulator status    # its state, and in a KMP repository whether its tests use it
shogun simulator stop
```

### KMP tests

KMP's iOS simulator tests pick their device in the build script, so add this to the module's `build.gradle.kts`:

```kotlin
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    providers.environmentVariable("IOS_SIMULATOR_UDID").orNull?.let { device.set(it) }
}
```

If you'd rather not touch the repository, `shogun simulator gradle-init install` (or the button in the project configuration) installs a Gradle init script that does the same for every project on this machine, after asking. Once installed, the project configuration offers to uninstall it, like `shogun simulator gradle-init uninstall`.

- The project configuration also chooses the device type and runtime per machine.
- "Clean up" (or `shogun clean --delete-simulators`) deletes the simulators of removed worktrees and ShogunAi simulators whose worktree is gone.

## Capacity

Emulators, iOS simulators and Gradle daemons are limited per machine, shared by every repository and worktree. The limits come from the capacity profile in Preferences > Resources and are never read from the repository: **Automatic** (the default, from the machine's memory and cores), **Light** (1 emulator, 1 simulator, 2 Gradle daemons), **Balanced** (2, 2 and 4), **One per worktree** (no limit beyond the 16 emulator ports) or **Custom** (a number or no limit per kind). Both the app and `shogun` apply them.

- When you start a device and its pool is full, the least recently used idle one is released: a stopped emulator loses its port and a shut-down simulator is deleted. Running and booting devices are never released; if none is idle the start fails and names the worktrees that hold them.
- Gradle daemons are started by your builds, so they can't be refused: the app (every 30 seconds) and `shogun pool trim` stop the idle ones, longest idle first, until they are within the limit.
- `shogun pool status` shows each pool with its limit and holders, and the machine views of the app show the same with a notice for what was released.

## Xcode

Xcode keeps one DerivedData folder per project path, so every worktree gets its own (several gigabytes for a real app) and Xcode never removes the folder of a deleted worktree. Turn on the Xcode provider (macOS only):

```json
{ "providers": { "xcode": { "compilationCache": true } } }
```

The worktree screen then shows an "Xcode DerivedData" section with each worktree's folder, its size and last access, and the orphaned folders on the machine: those whose project no longer exists, whichever tool created them. The folders appear at once; their sizes and the totals read "Calculating…" until each folder has been measured in the background, one at a time, without freezing the app. The calculation goes on if you leave the view (the sidebar entry shows the total once it's ready) and starts over when you reload it. "Delete orphans" removes them, and "Clean up" (checked by default) also deletes the orphans after removing worktrees, including the folders of the worktrees it removes.

Unless you set a quota, only orphaned folders are ever deleted, and never the DerivedData of a project that still exists, never Xcode's shared caches (`CompilationCache.noindex`, `ModuleCache.noindex`, …), and never a folder whose project is on a volume that isn't mounted. ShogunAi finds DerivedData in Xcode's default location or the custom one set in Xcode's preferences.

### DerivedData quota

DerivedData grows with every worktree an agent builds in. Set a quota in Preferences > Resources (a number of gigabytes, or "No quota", the default) and ShogunAi keeps the folders within it. The quota is a machine setting, not part of the capacity profiles, and is never read from the repository.

When the folders add up to more than the quota, ShogunAi releases the orphaned folders first and then the least recently accessed folders of worktrees that are not in use, and stops as soon as the total fits. A worktree is in use while its agent is working or blocked, or its folder was accessed within the last hour. Folders of other projects, folders on unavailable volumes and the compilation cache are never released; if only those remain, the view says the quota is still exceeded and names the worktrees in use. The app applies the quota every time the folder sizes have been measured, and shows the usage against the quota, in the sidebar too, and the folders it released until you dismiss the notice. `shogun xcode trim` applies it from the command line.

```bash
shogun xcode                               # this repository's DerivedData folders, the orphans and the compilation cache size
shogun xcode clean                         # delete the orphaned folders
shogun xcode trim [--dry-run]              # release folders until DerivedData fits the quota
shogun clean --yes --delete-derived-data   # also delete them after removing worktrees
```

### Compilation cache

`compilationCache` is optional. With it, ShogunAi writes a machine-wide `~/.shogunai/xcode/compilation-cache.xcconfig` that turns on Xcode's compilation caching with Swift and Clang prefix mapping, and every worktree's environment gets `XCODE_XCCONFIG_FILE` pointing to it, so a new worktree's first `xcodebuild` reuses what the others already compiled (in a test project, 16.5 s down to 1.8 s). The section shows the size of the shared compilation cache, which Xcode grows without ShogunAi bounding it.

- It needs Xcode 26 or later and helps `.xcodeproj` and `.xcworkspace` projects; Swift packages got no reuse between worktrees.
- It reaches agents like any [per-worktree variable](../agents.md#per-worktree-environment). An `XCODE_XCCONFIG_FILE` you set yourself in that shell is replaced; declare it in `env` instead and it takes precedence over the provider's.
- Xcode opened from the Dock doesn't inherit the agent's shell. Set the build settings in the project if you want caching there too.
