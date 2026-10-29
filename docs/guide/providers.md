# Providers

A provider adds support for one kind of tooling to a repository's worktrees. A repository turns providers on in its [`.shogunai.json`](configuration.md#repository-configuration-shogunaijson) under `providers`; repositories without a provider never see any of its sections, buttons or commands, and none of its tools run.

```json
{ "providers": { "gradle": {}, "emulator": {}, "ios-simulator": {}, "xcode": {}, "maven": {}, "docker-compose": {}, "desktop-app": { "module": ":desktopApp" } } }
```

| Provider | For | Platforms |
|---|---|---|
| [`gradle`](#gradle) | Android, KMP and JVM repositories: Gradle daemons and their memory. | Any; the daemon list needs `ps` (macOS, Linux) |
| [`emulator`](#android-emulator) | An Android emulator per worktree. | Any with the Android SDK |
| [`ios-simulator`](#ios-simulator) | An iOS simulator per worktree. | macOS |
| [`xcode`](#xcode) | Xcode DerivedData, the shared compilation cache, Swift packages and CocoaPods pods. | macOS |
| [`maven`](#maven) | Maven builds: a memory bound and the Maven daemons. | Any; the daemon list needs `ps` (macOS, Linux) |
| [`docker-compose`](#docker-compose) | A Docker Compose project and ports per worktree. | Any with the Docker CLI |
| [`desktop-app`](#compose-desktop-app) | A Compose Desktop app per worktree, run in the background without taking the desktop. | macOS |

## Detecting stacks

You don't have to know which providers exist. ShogunAi looks at the files of the repository's main folder and its direct subfolders (never hidden folders, `build`, `node_modules` or `Pods`) and recognises five stacks:

| Stack | Revealed by | Suggests |
|---|---|---|
| Gradle | `gradlew`, `settings.gradle` or `settings.gradle.kts` in the main folder | `gradle` |
| Android | `com.android.` in a `build.gradle(.kts)` of those folders, or in `gradle/libs.versions.toml` | `emulator` |
| Xcode | an `.xcodeproj` folder, or `Package.swift` in the main folder | `ios-simulator` and `xcode` (macOS only) |
| Maven | `pom.xml` or `mvnw` in the main folder | `maven` |
| Docker Compose | `compose.yaml`, `compose.yml`, `docker-compose.yaml` or `docker-compose.yml` in the main folder | `docker-compose` |

In the project settings, "Repository configuration" lists the providers the repository could use and doesn't declare yet, with the files that revealed them, and "Turn on" switches the provider on as an unsaved change: nothing is written until you save and confirm the preview. `shogun stacks` prints the same from a terminal and changes nothing. A suggestion is only a hint (a `pom.xml` in a fixture folder also counts), so the evidence is always shown.

ShogunAi also looks, at any depth outside build and tool folders (`build`, `.gradle`, `node_modules`, `Pods`, `DerivedData`), for the untracked files a fresh mobile worktree can't build without: `local.properties`, `keystore.properties`, `key.properties`, `*.jks`, `*.keystore`, `google-services.json` and `GoogleService-Info.plist`. Each one nothing provisions yet (local files, `copy`, `link` or `.worktreeinclude`) is suggested as an anchored `link` pattern, such as `/app/google-services.json`, so rotating it in the base repository updates every worktree. It also reads the `storeFile` keys of those properties files (any key ending in `storeFile`, such as `RELEASE_STORE_FILE`): a keystore they name inside the repository is suggested too, and a relative path that leaves the repository is reported, because it won't resolve from a worktree that lives in another folder. The project settings list them under "Worktree files" and `shogun stacks` prints them; nothing is written until you accept and save.

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

## Maven

```json
{ "providers": { "maven": { "mavenMaxHeap": "1g" } } }
```

Parallel builds in several worktrees each start a JVM with Maven's default heap, and Maven daemons (mvnd) stay alive after the build. `mavenMaxHeap` is optional, a size such as `512m` or `1g`: with it, every worktree's environment (the main one included) gets `MAVEN_OPTS=-Xmx<size>`. A `MAVEN_OPTS` you declare in `env` takes precedence, one you set yourself in that shell is replaced, and builds started from an IDE don't inherit it.

For a Maven repository the app shows a "Maven daemons" section with every mvnd daemon on the machine: its memory, whether it's busy or idle, and the worktree of its last build (mvnd moves the daemon's working directory to each build's folder: the deepest worktree that contains it, "removed worktree" when the folder is gone inside the worktrees folder, "other project" otherwise, nothing when it can't be read). Plain `mvn` builds and IDEs are not listed.

The state comes from the daemon's own log, whose location mvnd puts on the daemon's command line; it works with mvnd 1.x and 2.x and any `mvnd.daemonStorage`. A daemon is busy while a build runs and idle afterwards; one whose log can't be read has an unknown state. You can stop idle daemons there, and "Clean up" offers to stop the idle Maven daemons of the worktrees it removes. A busy daemon, or one whose state is unknown, is never stopped; the state is read again just before stopping.

```bash
shogun maven                       # list the Maven daemons, their memory, state and worktree
shogun maven stop                  # stop every idle Maven daemon (or: shogun maven stop <pid>...)
shogun clean --yes --stop-maven-daemons  # also stop the idle Maven daemons of the removed worktrees
```

Daemon inspection needs `ps` and `lsof` (macOS and Linux).

## Docker Compose

```json
{ "providers": { "docker-compose": { "portVariables": ["DB_PORT", "API_PORT"] } } }
```

Two worktrees running `docker compose up` share container, network and volume names, and publish the same host ports. The provider gives each worktree its own:

- `COMPOSE_PROJECT_NAME` is `<repository folder>-<worktree folder>`, lowercased, with every character other than letters, digits, `-` and `_` replaced by `-` (`main-repo` and `TASK-123` give `main-repo-task-123`). A folder name that normalising would make ambiguous (`fix login (2)`, or mixed case) gets a short stable hash, and a name over 50 characters is shortened keeping it, so different worktrees get different names. A `COMPOSE_PROJECT_NAME` declared in `env` takes precedence.
- `portVariables` is optional: each name becomes a variable with a free port in 20000-29999 reserved for that worktree (the same rules as `${port}`), so the Compose file can publish `${DB_PORT}:5432` without colliding. Names follow the variable name rule and can't be reserved names; a name also declared in `env` gets the declared value and no port.

### Starting and building the services

`shogun compose up` starts the services of the worktree you're in, detached, with its `COMPOSE_PROJECT_NAME` and port variables, so you never pass `-p` or ports by hand; `--build` builds first, and everything after `--` goes to `docker compose` as is (service names, `--wait`). `shogun compose build` only builds. Docker Compose's output is shown as it runs. The worktree's detail in the app has a Compose section with the project's name and state (`Not started` when Docker knows no such project) and Start, Build and Take down actions. The SessionStart context tells Claude to use `shogun compose up`.

### What removed worktrees leave behind

Taking a project down keeps its volumes, and Compose never removes the images it built, so removed worktrees leave volumes, networks and images behind. ShogunAi records in `~/.shogunai/compose-projects.json` the project name it gives each worktree (when its environment is resolved and on every `up` or `build`); a volume, network or image whose `com.docker.compose.project` label is a recorded name whose worktree no longer exists is a leftover of that worktree. A name that no other worktree uses now and that no other repository has recorded is the only thing that counts, so projects of other repositories, even with a similar prefix, are never touched. Images pulled from a registry (such as `postgres`) carry no Compose label and are never removed.

The Machine section's "Compose projects" view lists the projects `docker compose ls` reports for the repository's worktrees (status and worktree), the projects of removed worktrees (recorded ones, and those whose compose files lived in the repository or its worktrees folder), and, after them, the leftovers of removed worktrees. "Take down the projects of removed worktrees" runs `docker compose -p <name> down`, which removes containers and networks and keeps volumes. "Remove networks and images" removes the leftovers except volumes; "Remove volumes too" asks first, because their data is lost. Nothing is ever forced: an image or network still used by a container is kept and reported.

"Clean up" has a checked option to take down the projects of the worktrees it removes and remove their networks and built images, and an unchecked one to remove their volumes too; if Docker isn't available the worktrees are still removed and the report says the projects were kept.

Worktrees removed before this registry existed weren't recorded: their projects with containers are still found by their compose files, and the rest can be removed with `docker volume rm` and `docker image rm`.

```bash
shogun compose up                  # start this worktree's services (detached) with its project name and ports
shogun compose up --build -- api   # build first, and only the api service
shogun compose build               # build this worktree's services
shogun compose                     # the Compose projects of the worktrees and what removed worktrees left
shogun compose down                # take down the projects of removed worktrees (volumes kept)
shogun compose down TASK-123       # take down one worktree's project
shogun compose prune               # list the networks and images left by removed worktrees
shogun compose prune --yes         # remove them (add --volumes to remove their volumes too)
shogun clean --yes --down-compose-projects  # also take down the removed worktrees' projects and remove their images
```

Docker is only run for repositories that declare the provider, and only the Docker CLI is used.

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
- When the worktree is open in Orca, starting its emulator also shows it in Orca's emulator view; see [Orca](../agents.md#orca).
- The SDK is found through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `sdk.dir` in `local.properties` or Android Studio's default location.
- The adb server is shared by every worktree: agents must never restart it (`adb kill-server`) or force a device with `-s`, `-e` or `-d`. The `SessionStart` hook tells Claude so.

## Compose Desktop app

An agent checking a Compose Desktop app with computer use takes the screen, mouse and keyboard away from you, and only one agent can do it at a time. Turn on the desktop app provider to let each worktree's agent run its app in the background instead:

```json
{ "providers": { "desktop-app": { "module": ":desktopApp" } } }
```

`module` is the Gradle path of the app's module, which must use Compose Hot Reload (Compose Multiplatform 1.12 or later). `isolatedHome` (default `true`) gives the app its own user home per worktree, so it never touches your own data; set it to `false` for an app that must read the real home.

```bash
shogun headless start      # launch the app hidden, without taking focus
shogun headless capture    # PNG of its window, even while hidden
shogun headless status
shogun headless stop
```

The agent drives the app through Compose Hot Reload's MCP server. See [Checking a desktop app in the background](headless-app.md) for the whole workflow, the macOS permissions it needs and what still needs the desktop.

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

- Every worktree that holds a device has a **lease** on it. Starting the device and every moment its agent is working renew the lease, and it expires after the lease duration without activity (20 minutes by default, set in Preferences > Resources, never in the repository). Only devices whose lease has expired can be released.
- When you start a device and its pool is full, ShogunAi releases one device with an expired lease, in this order (the least recently used first within each step): a stopped device whose worktree has no agent session, a stopped device whose agent is done or idle, a running device (stopped first) and, last, a device whose agent is blocked. A device whose agent is working, or whose lease is still valid, is never released; if none can be released the start fails and names the worktrees that hold them, with when each lease expires.
- `shogun emulator start --wait` and `shogun simulator start --wait` wait in line for a full pool instead of failing, for 30 minutes at most (`--wait-timeout <minutes>` changes it). Waiters are served first in, first out across the machine, and while anyone waits, other starts can't take the device they wait for. Agents should run a waiting start in the background; the session hint says so.
- A released emulator loses its port and a released simulator is deleted. The released worktree remembers why (`shogun emulator status` says "released for …"), its port isn't handed to another worktree while another one is free, and it gets the same port back when it starts again. Agents should run `shogun emulator start` before every use of the device: it is idempotent, re-acquires one if it was released and prints the serial to use. The session hint says so.
- Gradle daemons are started by your builds, so they can't be refused: the app (every 30 seconds) and `shogun pool trim` stop the idle ones, longest idle first, until they are within the limit.
- `shogun pool status` shows each pool with its limit, holders and waiters, and the machine views of the app show the same with a notice for what was released.

## Xcode

Xcode keeps one DerivedData folder per project path, so every worktree gets its own (several gigabytes for a real app) and Xcode never removes the folder of a deleted worktree. Turn on the Xcode provider (macOS only):

```json
{ "providers": { "xcode": { "compilationCache": true } } }
```

The worktree screen then shows an "Xcode DerivedData" section with each worktree's folder, its size and last access, and the orphaned folders on the machine: those whose project no longer exists, whichever tool created them. The folders appear at once; their sizes and the totals read "Calculating…" until each folder has been measured in the background, several at a time, without freezing the app. ShogunAi remembers each folder's size between runs, in the app and the `shogun` command alike, and only measures again the folders Xcode has used since, so after the first run most sizes appear at once. The calculation goes on if you leave the view (the sidebar entry shows the total once it's ready), and reloading the view measures every folder again, showing the previous sizes until the new ones arrive. "Delete orphans" removes them, and "Clean up" (checked by default) also deletes the orphans after removing worktrees, including the folders of the worktrees it removes.

Outside the quota, only orphaned folders are ever deleted, and never the DerivedData of a project that still exists, never Xcode's shared caches (`CompilationCache.noindex`, `ModuleCache.noindex`, …), and never a folder whose project is on a volume that isn't mounted. ShogunAi finds DerivedData in Xcode's default location or the custom one set in Xcode's preferences.

### DerivedData quota

DerivedData grows with every worktree an agent builds in, so ShogunAi keeps the folders within a quota, chosen in Preferences > Resources:

- **Automatic**, the default: a quarter of the space DerivedData could use on its disk (the free space plus what DerivedData already takes), between 20 and 200 GB. With 100 GB free and 60 GB of DerivedData, the quota is 40 GB. It's computed each time the quota is applied; if the free space can't be read, nothing is released and the view says the quota is unknown.
- **Fixed size**: a number of gigabytes.
- **No quota**: nothing is released to honour a quota; only orphans are deleted, when you ask.

The quota is a machine setting, not part of the capacity profiles, and is never read from the repository. Settings saved before the automatic quota existed keep their fixed size if they had one, and otherwise become Automatic.

When the folders add up to more than the quota, ShogunAi releases the orphaned folders first and then the least recently accessed folders of worktrees that are not in use, and stops as soon as the total fits. A worktree is in use while its agent is working or blocked, or its folder was accessed within the last hour. Folders of other projects, folders on unavailable volumes and the compilation cache are never released; if only those remain, the view says the quota is still exceeded and names the worktrees in use. The app applies the quota every time the folder sizes have been measured, and shows the usage against the quota (marked "automatic" for the automatic one), in the sidebar too, and the folders it released until you dismiss the notice. `shogun xcode trim` applies it from the command line.

```bash
shogun xcode                               # this repository's DerivedData folders with their packages, the orphans and the shared caches
shogun xcode clean                         # delete the orphaned folders
shogun xcode trim [--dry-run]              # release folders until DerivedData fits the quota
shogun clean --yes --delete-derived-data   # also delete them after removing worktrees
```

### Compilation cache

`compilationCache` is optional. With it, ShogunAi writes a machine-wide `~/.shogunai/xcode/compilation-cache.xcconfig` that turns on Xcode's compilation caching with Swift and Clang prefix mapping, and every worktree's environment gets `XCODE_XCCONFIG_FILE` pointing to it, so a new worktree's first `xcodebuild` reuses what the others already compiled (in a test project, 16.5 s down to 1.8 s). The section shows the size of the shared compilation cache, which Xcode grows without ShogunAi bounding it.

- It needs Xcode 26 or later and helps `.xcodeproj` and `.xcworkspace` projects; Swift packages got no reuse between worktrees.
- It reaches agents like any [per-worktree variable](../agents.md#per-worktree-environment). An `XCODE_XCCONFIG_FILE` you set yourself in that shell is replaced; declare it in `env` instead and it takes precedence over the provider's.
- Xcode opened from the Dock doesn't inherit the agent's shell. Set the build settings in the project if you want caching there too.

### Swift packages and package caches

Xcode resolves a project's Swift packages into its DerivedData folder (`SourcePackages`), so each worktree has its own checkouts, which count against the quota and go away with the folder. They come from Xcode's shared repository cache, so a new worktree resolves them without the network. The section and `shogun xcode` show how much of each worktree's folder are packages ("App.xcodeproj · packages 800 MB"), measured and remembered with the folder's size; checkouts aren't shared between worktrees.

The shared package caches, SwiftPM's (`~/Library/Caches/org.swift.swiftpm`) and CocoaPods' (`~/Library/Caches/CocoaPods`), are shown below the compilation cache when they exist. Like it, they don't count against the quota and ShogunAi never deletes them; only their default locations are measured.

### CocoaPods

A CocoaPods project's `Pods` folder is usually ignored by Git, so a fresh worktree can't build until `pod install` runs. Turn on `cocoaPods` and ShogunAi gives each new or provisioned worktree the base repository's pods:

```json
{ "providers": { "xcode": { "cocoaPods": true } } }
```

For every `Podfile.lock` tracked by Git, at any depth, whose worktree has no `Pods` yet, ShogunAi compares it with the base's `Pods/Manifest.lock`, the same check CocoaPods' "Check Pods Manifest.lock" build phase makes. When they match, the base's `Pods` is cloned into the worktree with APFS copy-on-write (`cp -c`): instant, and it takes no disk until a file changes. When they don't match, the base never ran `pod install`, or the volume can't clone, nothing is copied (never a slow full copy) and `shogun new`, `shogun provision` and the worktree creation hook say which folders need `pod install` and why (`pods` in their JSON). Pods are prepared after the files are copied and before the post-creation commands, so a `pod install` declared there finds them in place; a problem with the pods never fails the worktree.
