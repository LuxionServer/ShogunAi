# Roadmap

Ideas accepted for ShogunAi's future, in priority order. This page is a direction, not a commitment to dates: each item becomes an OpenSpec change (`/opsx:propose`) when it's picked up, and its design is decided there. Once a change is archived, its item is removed from this page.

## Direction

ShogunAi is evolving from a desktop app that creates worktrees into **the environment layer for AI agents working in parallel on one machine**: it gives each worktree everything its agent needs to build and run without colliding with the others, and gives you an overview of all of them, whoever created them. It doesn't embed terminals or run agents itself; Claude, Codex or any other agent keeps doing that, inside the worktrees ShogunAi prepares and oversees.

That direction is built in three layers:

| Layer | Role | What it brings |
|---|---|---|
| **Engine and CLI** | The foundation | Worktree provisioning defined in the repository (`.shogunai.json`) and a `shogun` command that the desktop app, agents and their hooks all use, including a per-worktree environment (reserved ports and variables) delivered to each agent's shell, and JSON output on every command for scripts. **Available.** |
| **Control tower** | The view | The desktop app shows the state of every worktree, including the ones created by other tools, keeps them clean and follows changes as they happen. It opens each one in the terminal, the IDE or Herdr with the project's agents, and stays one click away in the menu bar. **Available.** |
| **Platforms** | The content | Providers for the stacks where a worktree is hardest to prepare, starting with Android, KMP, iOS and JVM: Gradle, local secrets, emulators, simulators and IDEs. **Started**: Gradle repositories show their daemons, can stop the idle ones and bound the memory and lifetime of the daemons agents start, Android repositories can give each worktree its own emulator, KMP and iOS repositories its own iOS simulator, both acquired on demand within limits that follow a machine capacity profile, and Xcode repositories see and clean up their DerivedData and share compilations between worktrees. |

It plugs into the tools that run agents rather than replacing them: Claude Code's worktree and session hooks, and [Herdr](https://herdr.dev), the terminal multiplexer for agents, whose worktrees a ShogunAi plugin provisions and where the desktop app opens a worktree with its environment and agent and shows what each agent is doing. **Available.**

## Planned

Numbered in the order they will be picked up: the number is the priority.

### 1. A disk quota for Xcode DerivedData

DerivedData is the one resource of the machine's pools that is limited by disk instead of by count. A quota in Preferences, applied by reusing the resource pool model (release the least recently used folders of worktrees that are not in use when the quota is exceeded), would keep it from growing without bound.

### 2. Redesigned worktree states and row layout

The current groups don't tell apart what matters: a worktree with an agent hides most of the others, and the group of worktrees that need the user is the most useful one. The states will separate worktrees that need the user, worktrees that are new and were never started, worktrees stopped for another reason and worktrees with an agent working. The date in each row will say what it measures (today it's the last commit, which in a new worktree is inherited from the base branch and reads as activity), and the row's information will be ordered and given fixed sizes so it's easier to scan, without repeating the project name.

### 3. Platforms and IDEs beyond Android

The project grew around Android. This item reviews the coverage of the other stacks (iOS and Xcode, JVM backends) and of the IDEs a worktree can be opened in. It starts with an exploration to decide what's missing before it's split into changes.

### 4. Follow-ups of the 2026-10-19 changes

The review of the whole project on 2026-10-19 and the changes built after it are archived. These follow-ups were left out of them:

- Support for bare repositories with worktrees (the "bare clone" layout), which changes what the base repository and copying from it mean. Today ShogunAi rejects them with a specific error.
- Filesystem watchers instead of polling for Git metadata changes.
- Splitting `ProjectWorktrees.kt` and `ProjectSettingsView.kt` into smaller holders and sections.
- Build provenance attestations for release assets, and treating compiler warnings as errors now that the build is clean.
- More terminals for "Open in terminal" (Ghostty, Warp, Alacritty, Kitty, GNOME Terminal), each as one more adapter.
- Faster DerivedData sizes: measuring several folders in parallel and remembering sizes between app runs.
- A notification when a Herdr agent becomes blocked or done; today the worktree's row and the menu bar icon's menu are the signal.
- Finding the root cause of the errors that closed the Preferences window, from the entries the isolation now leaves in the diagnostics log, and of the native crash seen on another machine that left an empty diagnostic report.

### 5. Guided onboarding demo

A first-run walkthrough built on the review done before merging the worktree layer: the app creates a throwaway sample repository with worktrees in every state, and opens an agent with a prepared prompt that walks the user through each feature (status, cleanup, provisioning, the CLI, hooks, environment and providers), one step at a time.

### 6. Android, KMP and JVM provisioning

Provisioning for the stacks where a fresh worktree is furthest from building:

- `local.properties`, keystores and `google-services.json`.
- Showing the emulators ShogunAi starts inside Android Studio's Running Devices window, as the ones Android Studio starts itself, so several of them can be arranged in one window. It needs a spike: Android Studio may embed an external emulator started with the options it uses itself.

## Under exploration

### Integration with GitHub and task trackers

Creating a worktree from a GitHub issue or a tracker task (deriving the task id and title), and showing the pull request state of each branch (open, in review, CI failing, merged). The value is clear, but the integration model isn't yet: which providers, how to authenticate, and how much to rely on external CLIs such as `gh`. It needs more thought before becoming a change.

### Opt-in remote error reporting

Sending anonymous errors and crashes to a remote service, in the style of the crash reporters used in mobile apps. It would only exist as an explicit opt-in on top of the local diagnostics, and needs a decision on where reports go and how to guarantee that no paths, project names or branch names are sent, since the repository is public and the users are developers.

## Out of scope for now

### Running agents inside the app

ShogunAi doesn't embed terminals or manage agent sessions: agents and terminals already do that well. It focuses on the worktrees they work in.

### macOS signing and notarization

Left out for now: it requires a paid Apple developer program. Until then, the README explains how to open the unnotarized app.
