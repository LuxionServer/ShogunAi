# Roadmap

Ideas accepted for ShogunAi's future, in priority order.
This page is a direction, not a commitment to dates: each item becomes an OpenSpec change (`/opsx:propose`) when it's picked up, and its design is decided there.
Once a change is archived, its item is removed from this page.

## Direction

ShogunAi is evolving from a desktop app that creates worktrees into **the environment layer for AI agents working in parallel on one machine**: it gives each worktree everything its agent needs to build and run without colliding with the others, and gives you an overview of all of them, whoever created them.
It doesn't embed terminals or run agents itself; Claude, Codex or any other agent keeps doing that, inside the worktrees ShogunAi prepares and oversees.

That direction is built in three layers, and it plugs into the runners that run the agents.
This section only summarizes them: the detail of what works today lives in the specs and the release notes.

### Engine and CLI

The foundation.
Status: **Available**.

- Worktree provisioning defined in the repository (`.shogunai.json`).
- A `shogun` command that the desktop app, agents and their hooks all use.
- A per-worktree environment (reserved ports and variables) delivered to each agent's shell.
- JSON output on every command, for scripts.

### Control tower

The view.
Status: **Available**.

- The desktop app shows the state of every worktree, including the ones created by other tools.
- It keeps them clean and follows changes as they happen.
- It opens each one in the terminal, the IDE or Herdr with the project's agents.
- It stays one click away in the menu bar.
- Multirepos group the repositories of one product, so a change spans several of them with one worktree and one agent per repository, coordinated by an agent for the whole multirepo, from the CLI and the app.
- A multirepo can share variables between its repositories, such as the port of the backend each group's app talks to, edited from the CLI and the app.

### Platforms

The content: providers for the stacks where a worktree is hardest to prepare, starting with Android, KMP, iOS and JVM (Gradle, local secrets, emulators, simulators and IDEs).
Status: **Started**.

- **Gradle**: repositories show their daemons, can stop the idle ones and bound the memory and lifetime of the daemons agents start.
- **Android**: each worktree can get its own emulator.
- **KMP and iOS**: each worktree can get its own iOS simulator.
- **Device limits**: emulators and simulators are acquired on demand within limits that follow a machine capacity profile, with `--wait` to queue for a full pool.
- **Xcode**: repositories see and clean up their DerivedData, keep it within a disk quota (automatic by default), share compilations between worktrees and see how much of it are Swift packages next to the shared SwiftPM and CocoaPods caches.
- **CocoaPods**: projects get the base repository's pods cloned into each new worktree when its lock matches.
- **Compose Desktop**: apps run hidden in the background for agents to check on macOS.
- **Desktop turn**: agents take turns to drive the real desktop with `shogun desktop acquire --wait`, which Claude takes by itself before its own computer use or Orca's acts, only stopping when another worktree holds it.
  The holder and the queue show in the control tower and `shogun status`.
- **Physical devices**: Android phones and tablets, iPhones and iPads connected to the machine are recorded in an inventory, lent to agents by default and acquired one worktree at a time with `shogun device acquire`, under the same leases and `--wait` queue as emulators and simulators.
  They show in `shogun status`, a Devices view in the control tower and Orca's emulator view (Android).
- **Maven**: JVM backends get Maven builds bounded in memory, see their Maven daemons and can stop the idle ones.
- **Docker Compose**: JVM backends get a Docker Compose project and ports per worktree, started and built with `shogun compose up`, and cleaned up with the volumes, networks and images removed worktrees leave behind.
- **Flutter**: repositories get `flutter pub get` in every new worktree, with their Gradle build in `android/` handled by the Gradle and emulator providers.
- **Stack detection**: the app and `shogun stacks` detect a repository's stack and suggest the providers that fit, and the `local.properties`, keystores and Firebase files to link into every worktree.
- **Local files**: they can be linked, matched by patterns and kept in sync when the base folder loses files.

### Runners

ShogunAi plugs into the tools that run agents rather than replacing them.

#### Claude Code and Herdr

Status: **Available**.

- Claude Code's worktree and session hooks.
- [Herdr](https://herdr.dev), the terminal multiplexer for agents, whose worktrees a ShogunAi plugin provisions.
- The desktop app opens a worktree in Herdr with its environment and agent, and shows what each agent is doing.

#### Orca

[Orca](https://www.onorca.dev), a desktop app for running agents in worktrees, is joining them as a second first-class runner.
Status: **Available**.
What only Orca offers: **Planned**.

- A repository's worktrees open in Orca with their environment and agent.
- Orca's `orca.yaml` scripts provision and release its own worktrees.
- The control tower shows the states Orca reports, with the tool an agent waits on, its prompt and its last message, and the ports Orca sees each worktree listen on.
- A multirepo's groups and its multirepo agent open in Orca too, and "New change" can open its worktrees in either runner.
- A worktree's Android emulator shows up in Orca's emulator view when it starts or the worktree opens in Orca.
- The default runner is chosen in Settings, and `shogun open`, the workflow guide and the session-start context follow it.

## How priorities are set

Agent tools are learning to create worktrees, copy local files into them and show which session works where, so ShogunAi keeps that part small and reaches it through their hooks.
Priority goes to what none of them can do for the others: the environment and the machine's resources of every stack, shared fairly between agents from any vendor, declared once in the repository (`.shogunai.json`), and one view of all those agents.
Within that, what the people using ShogunAi run into comes first, and above all of it, what ShogunAi already does has to work on any machine: reliability comes before new features.

## Planned

Listed in the order they will be picked up: a section's place on the page is its priority, so the first one is next.

### Reliability on any machine

ShogunAi has to work the first time on a machine that isn't the one it's developed on, and every regression has to be caught before a release reaches anyone.
A demo on another Mac showed why: the app, opened from the Dock, gets macOS's minimal `PATH`, so it reported a `shogun` command installed by Homebrew as not on the `PATH`, the guided tour refused to start and Claude Code's hooks read as unusable; and creating a worktree failed while copying its local files.
Neither showed up on the developer's machine, whose own setup hides them.
In order:

- Tests that hold the product to it:
  - Raising the coverage floors that CI enforces up to 100%: the lines (93% today) and branches (87%) of the desktop app outside its composables, which the end-to-end tests cover.
    Lines of `core` and `cli`, branches of `cli`, every branch of `core` that some input can reach, and the desktop app's texts and project settings are already covered.
    The worktrees and their inspector are covered but for 41 branches of their list, rows and action bars, left for the shared components' change.
    The desktop app goes up one package per change, in this order: the shell and the menu bar icon, the machine views and multirepos, shared components, and last the app's wiring.

### Orca, a second first-class runner

[Orca](https://www.onorca.dev), the desktop app for running coding agents in worktrees, gets everything ShogunAi does with Herdr, plus what Orca offers that Herdr doesn't.
A spike compared the two: Orca's repository hooks (`orca.yaml`) can provision its worktrees with `shogun`, its CLI can open a ShogunAi worktree with its environment and agent, and it reports richer agent states that survive restarting the app.
In order:

- What only Orca offers:
  - Orca's board beyond what ShogunAi already keeps in step (status, a summary comment and GitHub pull request or issue links, while the desktop app runs or on an open): syncing without the desktop app, e.g. from Claude Code's hooks, custom board statuses, and Linear or GitLab links.
  - A group's worktrees shown as children of the multirepo agent's workspace.
    Orca 1.4.243 accepts a Git or "Open as Folder" workspace as the parent of an existing worktree, but not a folder workspace.
  - A worktree running both its emulator and its simulator shown in Orca's emulator view.
    Orca 1.4.243 shows one device per worktree and shuts down the simulator it replaces, so ShogunAi shows neither for now.
  - A physical iPhone or iPad shown in Orca's emulator view, once Orca can stream one.
- Checking:
  - That Orca streams a physical Android device attached by its transport serial, over USB and wireless debugging, and that the worktree's emulator keeps running.
  - On Windows and Linux (`orca-ide`), the CLI's location and the shell Orca's terminals run, which only tests with fakes cover.
  - That Orca detects an agent wrapped by Headroom.
  - That Orca's notifications and its mobile companion follow the agents ShogunAi starts in Orca.
- Left out on purpose: Orca's remote and SSH worktrees, since ShogunAi prepares worktrees on this machine; and Orca's orchestration runs, since the multirepo agent already coordinates a group.

### Provisioning and resources of mobile stacks

Provisioning and bounded resources for the stacks where a fresh worktree is furthest from building:

- Telling the desktop app's user which folders of a new worktree still need `pod install`; today the app clones the pods and only `shogun new`, `shogun provision` and the creation hook report what's missing.
- The packages of Swift package repositories built with `swift build` (their `.build` folder per worktree), which the Xcode provider doesn't see.
- Flutter follow-ups:
  - Checking the provider end to end on a real project made with `flutter create` (a worktree that builds with `flutter build apk --debug` and `flutter build ios --simulator` without manual steps).
  - Showing the size of the pub cache (`~/.pub-cache` or `PUB_CACHE`) next to the other shared caches.
  - Finding a Flutter SDK outside `PATH` from the desktop app started from Finder.
  - Dart mono-repositories with pub workspaces or Melos, which today declare their own bootstrap as a post-creation command.
- Deciding whether the session-start rule to run `shogun emulator start` before every use of the emulator is still needed now that the device lease is renewed while the session works (a long session that never sends a prompt can still lose its emulator), or whether `adb` failures should point to `shogun emulator start` instead, after watching it in use.
- Waiting for a device from the desktop app, and showing the worktrees waiting for each pool in its machine views; today only `shogun emulator start --wait` and `shogun simulator start --wait` wait, and the app fails a start while others wait.
- Physical devices follow-ups:
  - Checking them with real devices beyond an Android phone over USB: wireless debugging, an iPhone or iPad over USB and on the network, and plugging a recorded device in while a worktree waits for it.
  - Preferring a physical device or a virtual one automatically, instead of the agent choosing between `shogun device acquire` and `shogun emulator start`.
  - Uninstalling the project's app from a device when it's released.
  - Showing a worktree's physical devices in its row of the worktree list.
  - Giving the device's name more room in the Devices view, which cuts it at the default window width.

### Every agent, not only Claude Code

ShogunAi's view is only neutral if it sees every agent:

- Agent states for agents other than Claude Code (Codex, Gemini…), which have no equivalent hooks today.
- A notification when an agent (reported by Herdr or by Claude Code's hooks) becomes blocked or done; today the worktree's row and the menu bar icon's menu are the signal.
- Checking that Herdr detects an agent wrapped by Headroom (`headroom wrap claude`): opening in Herdr waits for Herdr to detect the agent it typed, and if Herdr doesn't see through the wrapper the open fails although the agent runs.
- Other agents' user-level files for the workflow guide, as their documentation confirms them (Cursor keeps user rules in its settings today), and a Settings section in the desktop app to install, update and remove the `AGENTS.md` block, as it already does for Claude Code's hooks.
- Recording the base branch of the worktrees Herdr creates, if its `worktree.created` event gains their start point; today `shogun base --set` records it by hand, while `shogun new`, Claude Code's hook, the desktop app and multirepo groups record it themselves.
- Showing each worktree's base branch in the desktop app's rows; today the Git column compares with it and names it in its tooltip and in the detail, and `shogun base` and `shogun status` report it.

### Multirepos for the whole team

Multirepos and groups by branch are done in the CLI and the desktop app, and they're also how people who don't write the code follow a change across repositories.
Left out:

- Carrying the multirepo agent set on this machine over when its folder moves: `shogun multirepo init` (or "Add multirepo") in the new folder registers it after reviewing it, but the local agent stays with the old entry and has to be set again.
- Associating worktrees whose branches don't match, e.g. `shogun group add <id> <worktree>`.
  It needs a design of its own: groups have no state today, and the association would reach group detection, the ports shared variables resolve against, cleanup and every group view.
- Showing the Claude session names of a group's worktrees and of the multirepo agent in the desktop app's views; today `shogun open --herdr` and `shogun status` report them, and the app only names the sessions it opens.
- A single session for the whole group, with a combined environment, as an option next to one agent per repository.
- The pull request state of each repository of a group, which depends on the GitHub integration under exploration.

### Robustness and maintenance

- Recognizing a branch squash-merged after resolving conflicts, or together with other branches, whose changes no longer match any commit of its target, for example by asking GitHub whether its pull request was merged.
  Squash and rebase merges whose changes match are detected.
- Finding the root cause of the errors that closed the Preferences window, from the entries the isolation now leaves in the diagnostics log, and of the native crash seen on another machine that left an empty diagnostic report.
- Checking on a real Windows machine that removing and cleaning up worktrees with build outputs deeper than 260 characters now succeeds (Git's long path support and the leftover fallback are covered by tests with fakes only).
- Moving the per-project ports of `ProjectWorktrees` that still run on the caller's dispatcher (emulator and simulator states, ports, releasing resources) off the interface thread, as the machine views now do.
- Reporting a broken or missing Herdr plugin in `shogun doctor` and the installation check, which today only say whether Herdr is installed; `shogun hooks status` and the hooks section of Settings already report it and reinstall it.
- Checking the state of Maven daemons on a real mvnd 2.x daemon: it was checked on mvnd 1.0.6, and 2.x is covered by its sources and by tests with its log argument.
- Filesystem watchers instead of polling for Git metadata changes.
- Support for bare repositories with worktrees (the "bare clone" layout), which changes what the base repository and copying from it mean.
  Today ShogunAi rejects them with a specific error.
- Splitting `ProjectWorktrees.kt` and `ProjectSettingsView.kt` into smaller holders and sections.
- Splitting `ShogunCli.kt` by command family, each family taking the shared `ShogunEngine`, and reviewing the few calls it still wires differently from the engine (agent detection in `remove` and `clean`, Gradle daemons with sessions, the clock of `xcode trim`).
- Moving the UI states and the logic that still live in `AppContainer` (`readComposeStates`, `startEmulatorShownInOrca`…) into their own holders, built on the shared `ShogunEngine`.
- Moving the other files in `~/.shogunai` (`compose-projects.json`, `orca.json`, `derived-data-sizes.json`, environment state, device queue, desktop turn, agent sessions, provisioning records and physical devices) to the versioned machine file that `projects.json`, `settings.json`, `trust.json` and `multirepos.json` use.
- Showing in the desktop app a settings change refused because `settings.json` was written by a newer version, before any change first raises that file's schema version.
- Checking the guided tour on Windows and Linux with a real installed app: creating the sample with the system's Git, and the tour's agent command, whose prompt is quoted for the shell each terminal runs.
- Removing Cmd/Ctrl+E, kept for one release as an alias of Cmd/Ctrl+O when the shortcut moved to the default runner.
- Writing the remaining state texts of the Machine views in the interface font: the Compose view still writes its leftovers title and a removed worktree as its owner in the data font.
- Taking the system out of the domain: the 13 domain files on the architecture tests' exception list still use `java.io.File`, `java.nio.file` or `System.getProperty` directly, instead of a port or an `osName` parameter.
- Doing less while the machine-wide state lock is held when acquiring an emulator or simulator: read devices before taking it, keep only the reservation under it, and stop, delete or create devices after releasing it.
  Every command run under the lock now has a time limit, but other processes still wait for those commands.
- A machine-wide state lock that can be awaited without pinning a thread, so cancelling a device acquisition reaches the work done under the lock.
- Letting cancellations through in the command-line tool and the desktop app as `core` now does, where `runCatching` still turns one into an ordinary failure.

## Later

Accepted, but left until someone needs them, because they widen what ShogunAi opens rather than the environment it prepares, and the tools agents run in are likely to cover them:

- The coverage of the IDEs a worktree can be opened in, starting with an exploration to decide what's missing.
- More terminals for "Open in terminal" (Ghostty, Warp, Alacritty, Kitty, GNOME Terminal), each as one more adapter.
- "Open in Claude Desktop" beyond macOS (whether its `claude://code/new?folder=` link accepts a folder on Windows is unverified).
- Showing the emulators ShogunAi starts inside Android Studio's Running Devices window, as the ones Android Studio starts itself.
  It needs a spike: Android Studio may embed an external emulator started with the options it uses itself.

## Under exploration

### Rethinking Settings

Settings should let people customize ShogunAi without making them configure everything: sensible defaults, a few top-level choices and the details only for whoever wants them.
That includes revisiting the provisional decision of tying the app's refresh cadence to the capacity profiles (see [Refresh cadence](guide/providers.md#refresh-cadence)), which reuses the profiles meant for device and daemon limits instead of offering a choice of its own.
The UX review of the v2 interface leaves material for it:

- The Automatic and Balanced capacity profiles barely differ.
- The device lease and desktop turn minutes are agents' internals that everyone sees; they could move into a collapsed "Advanced" subsection.
- Resources shows even when no project uses devices; it could show only when a project declares providers.
- The window is called "Preferences", while macOS and the projects say "Settings".
- Once the attention model ships, the idle threshold matters more, since it decides when unfinished work stops asking for attention.

### Background desktop apps beyond macOS and the keyboard

Compose Desktop apps can be checked in the background on macOS (`shogun headless` and the `desktop-app` provider), but two gaps remain.
Real keyboard input doesn't reach an app whose window isn't active: a bridge that posts events to the app's own queue, or a key tool in Compose Hot Reload's MCP server (a feature request to JetBrains), could close it.
And hiding and capturing a window are macOS-only: Windows and Linux need their own way to keep the app out of sight and capture its window, or a virtual display.
Each needs a spike before becoming a change.
The desktop turn that agents take for computer use (`shogun desktop`) is cooperative and only for agents: waiting for it from the desktop app, and noticing that the person is using the desktop so agents wait for them too, are left for when the turn is in use.

### A contract for external tracking tools

Tools outside ShogunAi, such as a follow-up view for a product owner, could read how a change progresses in each repository from GitHub alone if the conventions ShogunAi follows were written down as a contract: branches named after the task id (`feature/TASK-123`) in every repository the change touches, how to infer each branch's base without local state (`develop`, or `epic/<name>` for a change branched from an epic), and where an approximate progress is read from, either the checked tasks of the change's OpenSpec `tasks.md`, each naming the repository it touches, or a trailer in the commits (`Progress: 3/9`) written by a skill or the workflow guide for agents that don't plan with OpenSpec.
It needs the first such tool to say what it reads before it becomes a change.

### Integration with GitHub and task trackers

Creating a worktree from a GitHub issue or a tracker task (deriving the task id and title), and showing the pull request state of each branch (open, in review, CI failing, merged).
The value is clear, but the integration model isn't yet: which providers, how to authenticate, and how much to rely on external CLIs such as `gh`.
It needs more thought before becoming a change.

### Opt-in remote error reporting

Sending anonymous errors and crashes to a remote service, in the style of the crash reporters used in mobile apps.
It would only exist as an explicit opt-in on top of the local diagnostics, and needs a decision on where reports go and how to guarantee that no paths, project names or branch names are sent, since the repository is public and the users are developers.

## Out of scope for now

### A follow-up view for people who don't run agents

A product owner or a manager who wants to know which repositories of a task are done (the backend merged, Android in review, iOS still in progress) doesn't run agents or keep clones, so a read-only mode of the control tower would serve a different audience with almost none of ShogunAi's domain.
It's being explored as a separate project built on GitHub's API, and ShogunAi doesn't grow in that direction: its part is the contract for external tracking tools under exploration.

### Running agents inside the app

ShogunAi doesn't embed terminals or manage agent sessions: agents and terminals already do that well.
It focuses on the worktrees they work in.
