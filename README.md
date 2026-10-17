# ShogunAi

**English** · [Español](./README.es.md)

Desktop orchestrator that automates creating and managing isolated **Git worktrees**, with automatic provisioning of local secrets — designed to remove the manual context switching when you (or an AI agent like Claude/Copilot) work on several tasks at once.

## What it does

- Creates and removes isolated **Git worktrees** for each task, each on its own new branch (`feature/` or `fix/`) or on a branch that already exists locally.
- Automatically copies to each new worktree the **local files** it needs to build (e.g. Android's `local.properties`, `.env` files), as declared in a `.shogunai.json` versioned in the repository or in Claude Code's `.worktreeinclude`.
- Manages several projects from a catalog, each with its own configuration (base repository, worktrees folder, agent command).
- Copies the command to start your AI agent in a worktree (`cd <worktree> && claude`, optionally wrapped with Headroom), ready to paste in any terminal.
- If you move a repository, update its path in the project and the app re-links its worktrees (`git worktree repair`).
- Includes the `shogun` command, to create, provision, inspect and clean worktrees from a terminal, a script or an agent hook.

## Installation

### macOS (Homebrew)

```bash
brew tap LuxionServer/shogunai
brew install --cask shogunai
```

> [!NOTE]
> Homebrew picks the installer for your Mac's architecture (Apple Silicon or Intel) automatically. The app isn't notarized by Apple: if macOS warns that "the developer cannot be verified" the first time you open it, open it with right click → Open, or run `xattr -dr com.apple.quarantine "/Applications/ShogunAi.app"`.

### Manual download

Download the installer for your operating system from the [latest release](https://github.com/LuxionServer/ShogunAi/releases/latest):

| Operating system | Installer |
|---|---|
| macOS (Apple Silicon) | `-arm64.dmg` |
| macOS (Intel) | `-x64.dmg` |
| Windows | `.msi` |
| Linux | `.deb` |

Install it like any other desktop app and open it. You don't need a JDK or any development tools: the installer bundles its own runtime.

### The `shogun` command

- **Homebrew**: installed with the Cask; `shogun --version` works in a new terminal.
- **Linux (`.deb`)**: the app's launcher is the command. Link it once: `sudo ln -s /opt/shogunai/bin/ShogunAi /usr/local/bin/shogun`.
- **Windows, or without installing the app**: download `shogun-<version>.zip` from the release, unzip it and add its `bin` folder to your `PATH`. It needs Java 21 or later.

## Basic usage

1. **Create a project** — the first time you open the app it offers to add one. Click "Add project" (also in the sidebar and in File, Shift+Cmd/Ctrl+N) and enter a name, the path of the base repository and the folder where worktrees will be created. Optionally add local files that must be copied to each new worktree on this machine.
2. **Open a project** — select it in the sidebar to see its existing worktrees. Right-click it to edit or remove it; removing a project never touches its repository or worktrees.
3. **Create a worktree** — enter the task id and the branch type (`feature` or `fix`); the app creates the worktree and the branch, and copies the files the repository and the project declare. To work on a branch you already have, switch to "Existing branch" and pick it from the list.
   To share what a worktree needs with everyone who clones the repository, add a `.shogunai.json` at its root with gitignore-style patterns; every file Git doesn't track that matches one is copied into each new worktree, at the same path. A `.worktreeinclude` (Claude Code's file) is read the same way. The "Move to .shogunai.json" button in the project configuration writes your local files into it.

   ```json
   { "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"] }
   ```

4. **Start working** — click "Copy command" on a worktree and paste it in your terminal to open your AI agent there.
5. **Remove a worktree** — when you finish a task, remove it from the list, optionally deleting the local branch too. The main worktree can't be removed.

The app is in English by default; you can switch to Spanish in Preferences (Settings… in the ShogunAi menu on macOS, File > Preferences elsewhere, Cmd/Ctrl+Comma), next to the theme. Your choice is remembered between restarts, as are the last project you had open, the window's size and position, and whether the sidebar is collapsed (View > Toggle sidebar, Cmd/Ctrl+Backslash).

## Command line

`shogun` works on the Git repository that contains the current directory, using its project from the desktop app if there is one:

```bash
shogun new TASK-123                # new feature/TASK-123 worktree, provisioned; prints its path
shogun new --branch hotfix/login   # worktree for an existing branch
shogun provision ../other-worktree # copy the declared files into a worktree made by another tool
shogun status                      # every worktree with its changes, divergence and merged state
shogun clean --yes                 # remove merged or missing worktrees (without --yes it only lists them)
eval "$(shogun env)"               # load this worktree's variables from .shogunai.json "env" (ports, …)
```

Run `shogun help` for every option. Without a project in the app, `shogun new` needs `--worktrees-root <dir>`.

### With AI agents

Make `claude --worktree` create its worktrees through ShogunAi by adding the hooks to `.claude/settings.json`:

```json
{
  "hooks": {
    "WorktreeCreate": [{ "hooks": [{ "type": "command", "command": "shogun hook worktree-create" }] }],
    "WorktreeRemove": [{ "hooks": [{ "type": "command", "command": "shogun hook worktree-remove" }] }],
    "SessionStart": [{ "hooks": [{ "type": "command", "command": "shogun hook session-start" }] }]
  }
}
```

`SessionStart` gives each session its worktree's own variables, such as a dev server port, declared in `.shogunai.json` (`"env": { "PORT": "${port}" }`). Recipes for Cursor, Codex, direnv and any other tool are in [Using with agents](./docs/agents.md).

## Gradle repositories

Declare a Gradle (Android, KMP or JVM) repository in its `.shogunai.json`:

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

### Daemon memory and lifetime per worktree

Parallel builds can't share a busy daemon, so three agents building at once start three daemons, each with the heap your `gradle.properties` asks for, and they stay alive for three hours. The provider can bound the daemons that worktree builds start:

```json
{ "providers": { "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30, "kotlinDaemonMaxHeap": "1g" } } }
```

Every setting is optional. Memory sizes are a number followed by `k`, `m` or `g`; the timeout is in minutes. With at least one, every worktree's environment (the main one included) gets the same `GRADLE_OPTS`, which overrides `gradle.properties` for builds started from that shell. `daemonMaxHeap` only replaces `-Xmx`: the other `org.gradle.jvmargs` of your user `gradle.properties`, or else the repository's, are kept. The "Gradle daemons" section shows the settings in effect.

- It reaches agents like any [per-worktree variable](./docs/agents.md#per-worktree-environment). A `GRADLE_OPTS` you set yourself in that shell is replaced; declare it in `env` instead and it takes precedence over the provider's.
- Builds started from Android Studio or IntelliJ don't inherit the agent's shell: they keep using `gradle.properties` and don't share daemons with agent builds. Put the limits in `gradle.properties` too if you want them everywhere.
- New settings apply to new daemons; idle daemons started with other arguments aren't reused and time out on their own, or stop them from the section.
- The quoting is designed for Windows' `gradlew.bat` too, but has only been verified on macOS.

Daemon inspection needs `ps` (macOS and Linux).

## Android emulators

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

On each machine, the project configuration chooses the AVD (or leaves it automatic) and whether it runs without a window; `--avd` and `--no-window` override them for one start. The worktree list shows each emulator with Start and Stop, and "Clean up" (or `shogun clean --stop-emulators`) stops the emulators of the worktrees it removes. The SDK is found through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `sdk.dir` in `local.properties` or Android Studio's default location. Repositories without the provider never see any of this.

## iOS simulators

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

KMP's iOS simulator tests pick their device in the build script, so add this to the module's `build.gradle.kts`:

```kotlin
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    providers.environmentVariable("IOS_SIMULATOR_UDID").orNull?.let { device.set(it) }
}
```

If you'd rather not touch the repository, `shogun simulator gradle-init install` (or the button in the project configuration) installs a Gradle init script that does the same for every project on this machine, after asking. The project configuration also chooses the device type and runtime per machine. "Clean up" (or `shogun clean --delete-simulators`) deletes the simulators of removed worktrees and ShogunAi simulators whose worktree is gone. Repositories without the provider never see any of this.

## Xcode repositories

Xcode keeps one DerivedData folder per project path, so every worktree gets its own (several gigabytes for a real app) and Xcode never removes the folder of a deleted worktree. Turn on the Xcode provider (macOS only):

```json
{ "providers": { "xcode": { "compilationCache": true } } }
```

The worktree screen then shows an "Xcode DerivedData" section with each worktree's folder, its size and last access, and the orphaned folders on the machine: those whose project no longer exists, whichever tool created them. "Delete orphans" removes them, and "Clean up" (checked by default) also deletes the orphans after removing worktrees, including the folders of the worktrees it removes. Only orphaned folders are ever deleted: never the DerivedData of a project that still exists, never Xcode's shared caches (`CompilationCache.noindex`, `ModuleCache.noindex`, …), and never a folder whose project is on a volume that isn't mounted. ShogunAi finds DerivedData in Xcode's default location or the custom one set in Xcode's preferences.

```bash
shogun xcode                               # this repository's DerivedData folders, the orphans and the compilation cache size
shogun xcode clean                         # delete the orphaned folders
shogun clean --yes --delete-derived-data   # also delete them after removing worktrees
```

`compilationCache` is optional. With it, ShogunAi writes a machine-wide `~/.shogunai/xcode/compilation-cache.xcconfig` that turns on Xcode's compilation caching with Swift and Clang prefix mapping, and every worktree's environment gets `XCODE_XCCONFIG_FILE` pointing to it, so a new worktree's first `xcodebuild` reuses what the others already compiled (in a test project, 16.5 s down to 1.8 s). The section shows the size of the shared compilation cache, which Xcode grows without ShogunAi bounding it.

- It needs Xcode 26 or later and helps `.xcodeproj` and `.xcworkspace` projects; Swift packages got no reuse between worktrees.
- It reaches agents like any [per-worktree variable](./docs/agents.md#per-worktree-environment). An `XCODE_XCCONFIG_FILE` you set yourself in that shell is replaced; declare it in `env` instead and it takes precedence over the provider's.
- Xcode opened from the Dock doesn't inherit the agent's shell. Set the build settings in the project if you want caching there too.

Repositories without the provider, and other operating systems, never see any of this.

## Development

Want to build the project from source or contribute? Start with [`docs/getting-started.md`](./docs/getting-started.md).

## Documentation

Architecture and design decisions in [`/docs`](./docs) (English and Spanish), served with MkDocs:

```bash
pip install -r requirements-docs.txt
mkdocs serve
```

## License

[GNU GPLv3](./LICENSE)
