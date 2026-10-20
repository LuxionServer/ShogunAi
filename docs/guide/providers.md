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

`avd` is optional: the AVD this repository prefers when the machine has it. Each worktree then reserves an emulator port and gets `ANDROID_SERIAL=emulator-<port>` in its environment (`shogun env`, the `SessionStart` hook, `envFile`). Emulators only start when you ask:

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

Both options are optional preferences used when the machine has them. Each worktree gets a new simulator (created, never cloned: about 17 MB, 360 MB once booted) named `ShogunAi · <repository> · <worktree>`, and `IOS_SIMULATOR_UDID` in its environment. Build for it with `xcodebuild -destination "id=$IOS_SIMULATOR_UDID"`.

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

## Xcode

Xcode keeps one DerivedData folder per project path, so every worktree gets its own (several gigabytes for a real app) and Xcode never removes the folder of a deleted worktree. Turn on the Xcode provider (macOS only):

```json
{ "providers": { "xcode": { "compilationCache": true } } }
```

The worktree screen then shows an "Xcode DerivedData" section with each worktree's folder, its size and last access, and the orphaned folders on the machine: those whose project no longer exists, whichever tool created them. The folders appear at once; their sizes and the totals read "Calculating…" until each folder has been measured in the background, one at a time, without freezing the app. The calculation goes on if you leave the view (the sidebar entry shows the total once it's ready) and starts over when you reload it. "Delete orphans" removes them, and "Clean up" (checked by default) also deletes the orphans after removing worktrees, including the folders of the worktrees it removes.

Only orphaned folders are ever deleted: never the DerivedData of a project that still exists, never Xcode's shared caches (`CompilationCache.noindex`, `ModuleCache.noindex`, …), and never a folder whose project is on a volume that isn't mounted. ShogunAi finds DerivedData in Xcode's default location or the custom one set in Xcode's preferences.

```bash
shogun xcode                               # this repository's DerivedData folders, the orphans and the compilation cache size
shogun xcode clean                         # delete the orphaned folders
shogun clean --yes --delete-derived-data   # also delete them after removing worktrees
```

### Compilation cache

`compilationCache` is optional. With it, ShogunAi writes a machine-wide `~/.shogunai/xcode/compilation-cache.xcconfig` that turns on Xcode's compilation caching with Swift and Clang prefix mapping, and every worktree's environment gets `XCODE_XCCONFIG_FILE` pointing to it, so a new worktree's first `xcodebuild` reuses what the others already compiled (in a test project, 16.5 s down to 1.8 s). The section shows the size of the shared compilation cache, which Xcode grows without ShogunAi bounding it.

- It needs Xcode 26 or later and helps `.xcodeproj` and `.xcworkspace` projects; Swift packages got no reuse between worktrees.
- It reaches agents like any [per-worktree variable](../agents.md#per-worktree-environment). An `XCODE_XCCONFIG_FILE` you set yourself in that shell is replaced; declare it in `env` instead and it takes precedence over the provider's.
- Xcode opened from the Dock doesn't inherit the agent's shell. Set the build settings in the project if you want caching there too.
