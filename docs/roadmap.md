# Roadmap

Ideas accepted for ShogunAi's future, in priority order. This page is a direction, not a commitment to dates: each item becomes an OpenSpec change (`/opsx:propose`) when it's picked up, and its design is decided there. Once a change is archived, its item is removed from this page.

## Direction

ShogunAi is evolving from a desktop app that creates worktrees into **the environment layer for AI agents working in parallel on one machine**: it gives each worktree everything its agent needs to build and run without colliding with the others, and gives you an overview of all of them, whoever created them. It doesn't embed terminals or run agents itself; Claude, Codex or any other agent keeps doing that, inside the worktrees ShogunAi prepares and oversees.

That direction is built in three layers:

| Layer | Role | What it brings |
|---|---|---|
| **Engine and CLI** | The foundation | Worktree provisioning defined in the repository (`.shogunai.json`) and a `shogun` command that the desktop app, agents and their hooks all use, including a per-worktree environment (reserved ports and variables) delivered to each agent's shell, and JSON output on every command for scripts. **Available.** |
| **Control tower** | The view | The desktop app shows the state of every worktree, including the ones created by other tools, keeps them clean and follows changes as they happen. It opens each one in the terminal, the IDE or Herdr with the project's agents, and stays one click away in the menu bar. Multirepos group the repositories of one product, so a change spans several of them with one worktree and one agent per repository, coordinated by an agent for the whole multirepo, from the CLI and the app. **Available.** |
| **Platforms** | The content | Providers for the stacks where a worktree is hardest to prepare, starting with Android, KMP, iOS and JVM: Gradle, local secrets, emulators, simulators and IDEs. **Started**: Gradle repositories show their daemons, can stop the idle ones and bound the memory and lifetime of the daemons agents start, Android repositories can give each worktree its own emulator, KMP and iOS repositories its own iOS simulator, both acquired on demand within limits that follow a machine capacity profile, with `--wait` to queue for a full pool, Xcode repositories see and clean up their DerivedData, keep it within a disk quota, automatic by default, and share compilations between worktrees, and JVM backends get Maven builds bounded in memory and a Docker Compose project and ports per worktree, started and built with `shogun compose up` and cleaned up with the volumes, networks and images removed worktrees leave behind. The app and `shogun stacks` also detect a repository's stack and suggest the providers that fit, and the `local.properties`, keystores and Firebase files to link into every worktree; local files can be linked, matched by patterns and kept in sync when the base folder loses files. |

It plugs into the tools that run agents rather than replacing them: Claude Code's worktree and session hooks, and [Herdr](https://herdr.dev), the terminal multiplexer for agents, whose worktrees a ShogunAi plugin provisions and where the desktop app opens a worktree with its environment and agent and shows what each agent is doing. **Available.**

## How priorities are set

Agent tools are learning to create worktrees, copy local files into them and show which session works where, so ShogunAi keeps that part small and reaches it through their hooks. Priority goes to what none of them can do for the others: the environment and the machine's resources of every stack, shared fairly between agents from any vendor, declared once in the repository (`.shogunai.json`), and one view of all those agents. Within that, what the people using ShogunAi run into comes first.

## Planned

Numbered in the order they will be picked up: the number is the priority.

### 1. A turn for the desktop

Some things can only be checked by an agent driving the real app with computer use, and the desktop (screen, mouse, keyboard and the focused app) is one resource for the whole machine: two agents using it at once step on each other and on the person working there. A desktop turn, on the same model as the device leases and their `--wait` queue:

- `shogun desktop acquire --wait` and `shogun desktop release`, so only one agent drives the desktop at a time and the others wait in line instead of failing or colliding.
- A turn that expires on its own, so an agent that stops or crashes doesn't keep the desktop.
- Who holds the turn and who is waiting, in the control tower and in `shogun status`.

### 2. The environment of backends and services

Every worktree of a backend needs its own running services, not only its own code. Stack detection, the Maven and Docker Compose providers, the per-worktree ports and the Compose lifecycle (`up`, `build` and the cleanup of the volumes, networks and images left by removed worktrees) are done; what remains:

- Stopping Maven daemons once their state can be read reliably.
- Variables shared between the repositories of a multirepo group, for example the port of the backend the group's app talks to.

### 3. Provisioning and resources of mobile stacks

Provisioning and bounded resources for the stacks where a fresh worktree is furthest from building:

- The caches of iOS packages.
- Finding out what a Flutter repository needs in a worktree and which of the Android and iOS providers it can reuse.
- Waiting for a device from the desktop app, and showing the worktrees waiting for each pool in its machine views; today only `shogun emulator start --wait` and `shogun simulator start --wait` wait, and the app fails a start while others wait.

### 4. Every agent, not only Claude Code

ShogunAi's view is only neutral if it sees every agent:

- Agent states for agents other than Claude Code (Codex, Gemini…), which have no equivalent hooks today.
- A notification when an agent (reported by Herdr or by Claude Code's hooks) becomes blocked or done; today the worktree's row and the menu bar icon's menu are the signal.
- Other agents' user-level files for the workflow guide, as their documentation confirms them (Cursor keeps user rules in its settings today), and a Settings section in the desktop app to install, update and remove the `AGENTS.md` block, as it already does for Claude Code's hooks.
- Recording the origin ref of each worktree, so the rule for choosing a pull request's base could be answered by `shogun` instead of staying a team policy.

### 5. Multirepos for the whole team

Multirepos and groups by branch are done in the CLI and the desktop app, and they're also how people who don't write the code follow a change across repositories. Left out:

- Carrying the multirepo agent set on this machine over when its folder moves: `shogun multirepo init` (or "Add multirepo") in the new folder registers it after reviewing it and forgets the old entry, but the local agent goes with that entry and has to be set again.
- Associating worktrees whose branches don't match, e.g. `shogun group add <id> <worktree>`.
- A single session for the whole group, with a combined environment, as an option next to one agent per repository.
- Returning a declared workspace repository to detection (removing the `workspace` field), from the CLI and the multirepo settings; today `workspace clear` turns it off and the file has to be edited by hand to undo it.
- The pull request state of each repository of a group, which depends on the GitHub integration under exploration.

### 6. Robustness and maintenance

- Finding the root cause of the errors that closed the Preferences window, from the entries the isolation now leaves in the diagnostics log, and of the native crash seen on another machine that left an empty diagnostic report.
- Checking the in-app installation of the `shogun` command on a real installed app on Windows (the `shogun.cmd` and the user `PATH` change are covered by tests with fakes only) and on a machine without `~/.local/bin` on its `PATH`.
- Filesystem watchers instead of polling for Git metadata changes.
- Support for bare repositories with worktrees (the "bare clone" layout), which changes what the base repository and copying from it mean. Today ShogunAi rejects them with a specific error.
- Splitting `ProjectWorktrees.kt` and `ProjectSettingsView.kt` into smaller holders and sections.
- Checking the guided tour on Windows and Linux with a real installed app: creating the sample with the system's Git, and the tour's agent command, whose prompt is quoted for the shell each terminal runs.

## Later

Accepted, but left until someone needs them, because they widen what ShogunAi opens rather than the environment it prepares, and the tools agents run in are likely to cover them:

- The coverage of the IDEs a worktree can be opened in, starting with an exploration to decide what's missing.
- More terminals for "Open in terminal" (Ghostty, Warp, Alacritty, Kitty, GNOME Terminal), each as one more adapter.
- "Open in Claude Desktop" beyond macOS (whether its `claude://code/new?folder=` link accepts a folder on Windows is unverified).
- Showing the emulators ShogunAi starts inside Android Studio's Running Devices window, as the ones Android Studio starts itself. It needs a spike: Android Studio may embed an external emulator started with the options it uses itself.

## Under exploration

### Checking a running app without taking over the desktop

The desktop turn stops agents from colliding, but it doesn't make them parallel, and it still takes the screen away from the person. What can only be checked with computer use today needs another way to see and drive a running app: candidates are driving it through the operating system's accessibility API (pressing and reading its controls without moving the real cursor or stealing focus) together with capturing only its window, even in the background, or a test bridge inside the app that a provider talks to. It needs a spike on macOS to see how much of an app those reach, and how each one would fit other platforms.

### A follow-up view for people who don't run agents

People who don't write the code, such as a product owner or a manager, already follow changes through multirepo groups: which branches exist in each repository, how far each one is from its base and whether it's merged. A mode of the control tower for them would show that view, without the actions and agent states that only make sense on the machine where the agents run. It needs deciding what it reads (local clones, or only the remote branches) and how it relates to the pull request state of the GitHub integration below.

### Integration with GitHub and task trackers

Creating a worktree from a GitHub issue or a tracker task (deriving the task id and title), and showing the pull request state of each branch (open, in review, CI failing, merged). The value is clear, but the integration model isn't yet: which providers, how to authenticate, and how much to rely on external CLIs such as `gh`. It needs more thought before becoming a change.

### Opt-in remote error reporting

Sending anonymous errors and crashes to a remote service, in the style of the crash reporters used in mobile apps. It would only exist as an explicit opt-in on top of the local diagnostics, and needs a decision on where reports go and how to guarantee that no paths, project names or branch names are sent, since the repository is public and the users are developers.

## Out of scope for now

### Running agents inside the app

ShogunAi doesn't embed terminals or manage agent sessions: agents and terminals already do that well. It focuses on the worktrees they work in.

### macOS signing and notarization

Left out for now: it requires a paid Apple developer program. Until then, the README explains how to open the unnotarized app.
