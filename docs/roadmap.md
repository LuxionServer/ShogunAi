# Roadmap

Ideas accepted for ShogunAi's future, in priority order. This page is a direction, not a commitment to dates: each item becomes an OpenSpec change (`/opsx:propose`) when it's picked up, and its design is decided there. Once a change is archived, its item is removed from this page.

## Direction

ShogunAi is evolving from a desktop app that creates worktrees into **the environment layer for AI agents working in parallel on one machine**: it gives each worktree everything its agent needs to build and run without colliding with the others, and gives you an overview of all of them, whoever created them. It doesn't embed terminals or run agents itself; Claude, Codex or any other agent keeps doing that, inside the worktrees ShogunAi prepares and oversees.

That direction is built in three layers:

| Layer | Role | What it brings |
|---|---|---|
| **Engine and CLI** | The foundation | Worktree provisioning defined in the repository (`.shogunai.json`) and a `shogun` command that the desktop app, agents and their hooks all use, including a per-worktree environment (reserved ports and variables) delivered to each agent's shell, and JSON output on every command for scripts. **Available.** |
| **Control tower** | The view | The desktop app shows the state of every worktree, including the ones created by other tools, keeps them clean and follows changes as they happen. It opens each one in the terminal, the IDE or Herdr with the project's agents, and stays one click away in the menu bar. Multirepos group the repositories of one product, so a change spans several of them with one worktree and one agent per repository, coordinated by an agent for the whole multirepo, from the CLI and the app, and the multirepo can share variables between them, such as the port of the backend each group's app talks to, edited from the CLI and the app. **Available.** |
| **Platforms** | The content | Providers for the stacks where a worktree is hardest to prepare, starting with Android, KMP, iOS and JVM: Gradle, local secrets, emulators, simulators and IDEs. **Started**: Gradle repositories show their daemons, can stop the idle ones and bound the memory and lifetime of the daemons agents start, Android repositories can give each worktree its own emulator, KMP and iOS repositories its own iOS simulator, both acquired on demand within limits that follow a machine capacity profile, with `--wait` to queue for a full pool, Xcode repositories see and clean up their DerivedData, keep it within a disk quota, automatic by default, share compilations between worktrees and see how much of it are Swift packages next to the shared SwiftPM and CocoaPods caches, CocoaPods projects get the base repository's pods cloned into each new worktree when its lock matches, Compose Desktop apps run hidden in the background for agents to check on macOS, agents take turns to drive the real desktop with `shogun desktop acquire --wait`, which Claude takes by itself before its own computer use or Orca's acts, only stopping when another worktree holds it, with the holder and the queue in the control tower and `shogun status`, and JVM backends get Maven builds bounded in memory, see their Maven daemons and can stop the idle ones, and get a Docker Compose project and ports per worktree, started and built with `shogun compose up` and cleaned up with the volumes, networks and images removed worktrees leave behind, and Flutter repositories get `flutter pub get` in every new worktree, with their Gradle build in `android/` handled by the Gradle and emulator providers. The app and `shogun stacks` also detect a repository's stack and suggest the providers that fit, and the `local.properties`, keystores and Firebase files to link into every worktree; local files can be linked, matched by patterns and kept in sync when the base folder loses files. |

It plugs into the tools that run agents rather than replacing them: Claude Code's worktree and session hooks, and [Herdr](https://herdr.dev), the terminal multiplexer for agents, whose worktrees a ShogunAi plugin provisions and where the desktop app opens a worktree with its environment and agent and shows what each agent is doing. **Available.** [Orca](https://www.onorca.dev), a desktop app for running agents in worktrees, is joining it as a second first-class runner: a repository's worktrees open in Orca with their environment and agent, Orca's `orca.yaml` scripts provision and release its own worktrees, and the control tower shows the states Orca reports, with the tool an agent waits on, its prompt and its last message, and the ports Orca sees each worktree listen on; a multirepo's groups and its multirepo agent open in Orca too, and "New change" can open its worktrees in either runner; a worktree's Android emulator shows up in Orca's emulator view when it starts or the worktree opens in Orca; the default runner is chosen in Settings, and `shogun open`, the workflow guide and the session-start context follow it. **Available.** What only Orca offers. **Planned.**

## How priorities are set

Agent tools are learning to create worktrees, copy local files into them and show which session works where, so ShogunAi keeps that part small and reaches it through their hooks. Priority goes to what none of them can do for the others: the environment and the machine's resources of every stack, shared fairly between agents from any vendor, declared once in the repository (`.shogunai.json`), and one view of all those agents. Within that, what the people using ShogunAi run into comes first, and above all of it, what ShogunAi already does has to work on any machine: reliability comes before new features.

## Planned

Numbered in the order they will be picked up: the number is the priority.

### 1. Reliability on any machine

ShogunAi has to work the first time on a machine that isn't the one it's developed on, and every regression has to be caught before a release reaches anyone. A demo on another Mac showed why: the app, opened from the Dock, gets macOS's minimal `PATH`, so it reported a `shogun` command installed by Homebrew as not on the `PATH`, the guided tour refused to start and Claude Code's hooks read as unusable; and creating a worktree failed while copying its local files. Neither showed up on the developer's machine, whose own setup hides them. In order:

- Tests that hold the product to it:
  - Raising the coverage floors that CI enforces up to 100%: the branches of `core` (79% today) and `cli` (83%), and the lines (83%) and branches (67%) of the desktop app outside its composables, which the end-to-end tests cover. Lines of `core` and `cli` are already at 100%.
- A smoke test of the packaged app on a clean machine in CI before every release: install the `.dmg` on a macOS runner, open the app with the environment the Dock gives it, install the command and the hooks, add a project with local files, run `shogun new` and check the copied files. A release that fails it isn't published. Then the same on Windows and Linux with their packages, which also covers installing the command from the app on Windows and on a machine without `~/.local/bin` on its `PATH`, and choosing a folder with the system's dialog, which on Linux goes through the desktop portal and falls back to Java's chooser without one.
- More checks in `shogun doctor` and "Check Installation": the tools of the providers a repository declares (Xcode, the Android SDK, Docker), and multirepos with their repositories and groups.
- A step running `shogun doctor` in the [clean account rehearsal](process/clean-account-rehearsal.md), so a failure there arrives with what is wrong and how to fix it.

### 2. Fixes from the UX review of the v2 interface

A UX review of the implemented v2 interface, done on `develop` at `aaf42f94` with sample data, concluded that the app should first answer "what needs me?" and "where is each agent?", and that today it answers neither at a glance. Its fixes are split into changes, in order:

- The attention model (an OpenSpec change):
  - "Needs you" splits into "Waiting for you", for an agent that is blocked or done, under a warning header, and "Unfinished", for uncommitted or unpushed work, under a neutral one. The main checkout and idle worktrees leave both groups but keep their ✎ mark.
  - The sidebar's Active counter counts only "Waiting for you", and "Stopped" starts collapsed.
  - "↑" means only commits not pushed to the upstream: the comparison with the base moves to the inspector, and a branch without an upstream shows a "not pushed" icon.
  - A calm empty Active view: "Nothing needs you", with how many agents are working and "See all worktrees".
- One row per multirepo change in Active, showing the worst state of its repositories, which expands into one child row per repository; the child rows don't repeat the branch.
- A calmer list and inspector:
  - No inspector without a selection, with the summary moved to the view's header. The inspector docks when the content is at least 980 dp wide, and is an overlay below that.
  - The execution column collapses when no row uses it.
  - The origin shows only when it isn't ShogunAi, and the main checkout shows as its branch with a neutral "main" chip.
  - The worktree path is relative to the worktrees folder, with an ellipsis in the middle.
  - A section's summary shows only while the section is collapsed, and Provisioning appears only when something is stale or has failed.
  - The Agent section is one line when no agent runs, and keeps the running agent apart from "Runs with".
  - On narrow windows the type prefix truncates first, so the task id always shows, and the rail's tiles use two letters that tell sibling projects apart.
  - Counts inside sentences use the interface font with tabular figures; lone figures stay in the data font.
- Focusing an agent from its row: clicking the row's agent indicator, or pressing Enter on the row, focuses the agent's pane in its runner, and the tooltip says so.
- The machine's entry point and its alarms:
  - The status bar always starts with "Machine", and a gear at the bottom of the sidebar opens Settings.
  - A missing or optional tool shows as neutral with its reason; the danger color is kept for real failures.
  - The Gradle daemons view drops the state column when every value is unknown, explains why "Stop idle daemons" is disabled, and shows which worktree uses each daemon when that's known.
- New worktree's default start (a small OpenSpec change, already decided): the dialog starts from the main checkout's current branch, as `shogun new` does, instead of the project's main branch, fixing the spec and the desktop app guide with it.
- A tidier toolbar that fits in one row: the filter; "View ▾" with the project, grouping, order and Reload (also on ⌘R); "Clean up N" only when there is something to clean up; and New worktree. On narrow windows the filter becomes an icon and New worktree becomes "+".
- Quick fixes that don't need OpenSpec:
  - The status bar, its tooltip and the view take the count of Gradle daemons from a single source; today they disagree.
  - The guided tour's notice becomes one line in the sidebar's footer.
  - A legible "New" badge.
  - The command palette takes Settings and Preferences as synonyms and lists the worktrees for "open" when nothing is selected, drops fuzzy matches that aren't a subsequence of the visible name, and says in its footer, in the interface font, what ↵ and ⌘↵ do, taken from the action catalogue.
  - Checking by hand two issues, fixed only if they reproduce: a project added with `shogun project add` not appearing until the app restarts, and ⌘A followed by delete in the palette removing a single character.

### 3. Orca, a second first-class runner

[Orca](https://www.onorca.dev), the desktop app for running coding agents in worktrees, gets everything ShogunAi does with Herdr, plus what Orca offers that Herdr doesn't. A spike compared the two: Orca's repository hooks (`orca.yaml`) can provision its worktrees with `shogun`, its CLI can open a ShogunAi worktree with its environment and agent, and it reports richer agent states that survive restarting the app. In order:

- What only Orca offers:
  - Orca's board beyond what ShogunAi already keeps in step (status, a summary comment and GitHub pull request or issue links, while the desktop app runs or on an open): syncing without the desktop app, e.g. from Claude Code's hooks, custom board statuses, and Linear or GitLab links.
  - A group's worktrees shown as children of the multirepo agent's workspace. Orca 1.4.243 accepts a Git or "Open as Folder" workspace as the parent of an existing worktree, but not a folder workspace.
  - A worktree running both its emulator and its simulator shown in Orca's emulator view. Orca 1.4.243 shows one device per worktree and shuts down the simulator it replaces, so ShogunAi shows neither for now.
- Checking:
  - On Windows and Linux (`orca-ide`), the CLI's location and the shell Orca's terminals run, which only tests with fakes cover.
  - That Orca detects an agent wrapped by Headroom.
  - That Orca's notifications and its mobile companion follow the agents ShogunAi starts in Orca.
- Left out on purpose: Orca's remote and SSH worktrees, since ShogunAi prepares worktrees on this machine; and Orca's orchestration runs, since the multirepo agent already coordinates a group.

### 4. Provisioning and resources of mobile stacks

Provisioning and bounded resources for the stacks where a fresh worktree is furthest from building:

- Telling the desktop app's user which folders of a new worktree still need `pod install`; today the app clones the pods and only `shogun new`, `shogun provision` and the creation hook report what's missing.
- The packages of Swift package repositories built with `swift build` (their `.build` folder per worktree), which the Xcode provider doesn't see.
- Flutter follow-ups: checking the provider end to end on a real project made with `flutter create` (a worktree that builds with `flutter build apk --debug` and `flutter build ios --simulator` without manual steps); showing the size of the pub cache (`~/.pub-cache` or `PUB_CACHE`) next to the other shared caches; finding a Flutter SDK outside `PATH` from the desktop app started from Finder; and Dart mono-repositories with pub workspaces or Melos, which today declare their own bootstrap as a post-creation command.
- Deciding whether the session-start rule to run `shogun emulator start` before every use of the emulator is still needed now that the device lease is renewed while the session works (a long session that never sends a prompt can still lose its emulator), or whether `adb` failures should point to `shogun emulator start` instead, after watching it in use.
- Waiting for a device from the desktop app, and showing the worktrees waiting for each pool in its machine views; today only `shogun emulator start --wait` and `shogun simulator start --wait` wait, and the app fails a start while others wait.

### 5. Every agent, not only Claude Code

ShogunAi's view is only neutral if it sees every agent:

- Agent states for agents other than Claude Code (Codex, Gemini…), which have no equivalent hooks today.
- A notification when an agent (reported by Herdr or by Claude Code's hooks) becomes blocked or done; today the worktree's row and the menu bar icon's menu are the signal.
- Checking that Herdr detects an agent wrapped by Headroom (`headroom wrap claude`): opening in Herdr waits for Herdr to detect the agent it typed, and if Herdr doesn't see through the wrapper the open fails although the agent runs.
- Other agents' user-level files for the workflow guide, as their documentation confirms them (Cursor keeps user rules in its settings today), and a Settings section in the desktop app to install, update and remove the `AGENTS.md` block, as it already does for Claude Code's hooks.
- Recording the base branch of the worktrees Herdr creates, if its `worktree.created` event gains their start point; today `shogun base --set` records it by hand, while `shogun new`, Claude Code's hook, the desktop app and multirepo groups record it themselves.
- Showing each worktree's base branch in the desktop app's rows; today the Git column compares with it and names it in its tooltip and in the detail, and `shogun base` and `shogun status` report it.

### 6. Multirepos for the whole team

Multirepos and groups by branch are done in the CLI and the desktop app, and they're also how people who don't write the code follow a change across repositories. Left out:

- Checking every repository of a group before creating any of its worktrees: each repository's local files are checked before its own worktree is created, so a group whose second repository has an unavailable file keeps the worktree already created in the first.
- Carrying the multirepo agent set on this machine over when its folder moves: `shogun multirepo init` (or "Add multirepo") in the new folder registers it after reviewing it and forgets the old entry, but the local agent goes with that entry and has to be set again.
- Associating worktrees whose branches don't match, e.g. `shogun group add <id> <worktree>`. It needs a design of its own: groups have no state today, and the association would reach group detection, the ports shared variables resolve against, cleanup and every group view.
- Showing the Claude session names of a group's worktrees and of the multirepo agent in the desktop app's views; today `shogun open --herdr` and `shogun status` report them, and the app only names the sessions it opens.
- A single session for the whole group, with a combined environment, as an option next to one agent per repository.
- The pull request state of each repository of a group, which depends on the GitHub integration under exploration.

### 7. Robustness and maintenance

- Detecting squash and rebase merges, which leave a branch reading as not merged into its base or the main branch, so cleanup doesn't offer it and `--delete-branch` keeps it.
- Finding the root cause of the errors that closed the Preferences window, from the entries the isolation now leaves in the diagnostics log, and of the native crash seen on another machine that left an empty diagnostic report.
- Checking on a real Windows machine that removing and cleaning up worktrees with build outputs deeper than 260 characters now succeeds (Git's long path support and the leftover fallback are covered by tests with fakes only).
- Moving the per-project ports of `ProjectWorktrees` that still run on the caller's dispatcher (emulator and simulator states, ports, releasing resources) off the interface thread, as the machine views now do.
- Noticing a broken Herdr plugin: an install linked to a folder that no longer exists stops provisioning Herdr's worktrees without any error, so `shogun hooks status` and the app should check `herdr plugin list` and offer reinstalling it from the repository.
- Checking the state of Maven daemons on a real mvnd 2.x daemon: it was checked on mvnd 1.0.6, and 2.x is covered by its sources and by tests with its log argument.
- Filesystem watchers instead of polling for Git metadata changes.
- Support for bare repositories with worktrees (the "bare clone" layout), which changes what the base repository and copying from it mean. Today ShogunAi rejects them with a specific error.
- Splitting `ProjectWorktrees.kt` and `ProjectSettingsView.kt` into smaller holders and sections.
- Checking the guided tour on Windows and Linux with a real installed app: creating the sample with the system's Git, and the tour's agent command, whose prompt is quoted for the shell each terminal runs.
- A changelog page in the documentation site built from the release notes in `.github/release-notes/`, which the app already shows in its update notice.
- Removing Cmd/Ctrl+E, kept for one release as an alias of Cmd/Ctrl+O when the shortcut moved to the default runner.
- Showing the build that holds a busy Gradle daemon in the desktop app's Gradle view; today `shogun gradle` names it, with its client, tasks and the agent session that started it.
- Writing the remaining state texts of the Machine views in the interface font: the Compose view still writes its leftovers title and a removed worktree as its owner in the data font.

## Later

Accepted, but left until someone needs them, because they widen what ShogunAi opens rather than the environment it prepares, and the tools agents run in are likely to cover them:

- The coverage of the IDEs a worktree can be opened in, starting with an exploration to decide what's missing.
- More terminals for "Open in terminal" (Ghostty, Warp, Alacritty, Kitty, GNOME Terminal), each as one more adapter.
- "Open in Claude Desktop" beyond macOS (whether its `claude://code/new?folder=` link accepts a folder on Windows is unverified).
- Showing the emulators ShogunAi starts inside Android Studio's Running Devices window, as the ones Android Studio starts itself. It needs a spike: Android Studio may embed an external emulator started with the options it uses itself.

## Under exploration

### Rethinking Settings

Settings should let people customize ShogunAi without making them configure everything: sensible defaults, a few top-level choices and the details only for whoever wants them. That includes revisiting the provisional decision of tying the app's refresh cadence to the capacity profiles (see [Refresh cadence](guide/providers.md#refresh-cadence)), which reuses the profiles meant for device and daemon limits instead of offering a choice of its own. The UX review of the v2 interface (see point 2 above) leaves material for it:

- The Automatic and Balanced capacity profiles barely differ.
- The device lease and desktop turn minutes are agents' internals that everyone sees; they could move into a collapsed "Advanced" subsection.
- Resources shows even when no project uses devices; it could show only when a project declares providers.
- The window is called "Preferences", while macOS and the projects say "Settings".
- Once the attention model ships, the idle threshold matters more, since it decides when unfinished work stops asking for attention.

### Background desktop apps beyond macOS and the keyboard

Compose Desktop apps can be checked in the background on macOS (`shogun headless` and the `desktop-app` provider), but two gaps remain. Real keyboard input doesn't reach an app whose window isn't active: a bridge that posts events to the app's own queue, or a key tool in Compose Hot Reload's MCP server (a feature request to JetBrains), could close it. And hiding and capturing a window are macOS-only: Windows and Linux need their own way to keep the app out of sight and capture its window, or a virtual display. Each needs a spike before becoming a change. The desktop turn that agents take for computer use (`shogun desktop`) is cooperative and only for agents: waiting for it from the desktop app, and noticing that the person is using the desktop so agents wait for them too, are left for when the turn is in use.

### A follow-up view for people who don't run agents

People who don't write the code, such as a product owner or a manager, already follow changes through multirepo groups: which branches exist in each repository, how far each one is from its base and whether it's merged. A mode of the control tower for them would show that view, without the actions and agent states that only make sense on the machine where the agents run. It needs deciding what it reads (local clones, or only the remote branches) and how it relates to the pull request state of the GitHub integration below.

### Integration with GitHub and task trackers

Creating a worktree from a GitHub issue or a tracker task (deriving the task id and title), and showing the pull request state of each branch (open, in review, CI failing, merged). The value is clear, but the integration model isn't yet: which providers, how to authenticate, and how much to rely on external CLIs such as `gh`. It needs more thought before becoming a change.

### Opt-in remote error reporting

Sending anonymous errors and crashes to a remote service, in the style of the crash reporters used in mobile apps. It would only exist as an explicit opt-in on top of the local diagnostics, and needs a decision on where reports go and how to guarantee that no paths, project names or branch names are sent, since the repository is public and the users are developers.

## Out of scope for now

### Running agents inside the app

ShogunAi doesn't embed terminals or manage agent sessions: agents and terminals already do that well. It focuses on the worktrees they work in.
