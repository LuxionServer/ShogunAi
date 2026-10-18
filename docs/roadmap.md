# Roadmap

Ideas accepted for ShogunAi's future, grouped by theme. This page is a direction, not a commitment to dates: each item becomes an OpenSpec change (`/opsx:propose`) when it's picked up, and its design is decided there. Once a change is archived, its item is removed from this page.

## Direction

ShogunAi is evolving from a desktop app that creates worktrees into **the worktree layer for any AI agent**: it prepares each worktree so it's ready to build, and gives you an overview of all of them, whoever created them. It doesn't embed terminals or run agents itself; Claude, Codex or any other agent keeps doing that, inside the worktrees ShogunAi prepares and oversees.

That direction is built in three layers:

| Layer | Role | What it brings |
|---|---|---|
| **Engine and CLI** | The foundation | Worktree provisioning defined in the repository (`.shogunai.json`) and a `shogun` command that the desktop app, agents and their hooks all use, including a per-worktree environment (reserved ports and variables) delivered to each agent's shell. **Available**; richer provisioning is planned. |
| **Control tower** | The view | The desktop app shows the state of every worktree, including the ones created by other tools, keeps them clean and follows changes as they happen. **Available.** |
| **Android, KMP and JVM** | The content | Provisioning for the stacks where a worktree is hardest to prepare: Gradle, local secrets, emulators and IDEs. **Started**: Gradle repositories show their daemons, can stop the idle ones and bound the memory and lifetime of the daemons agents start, Android repositories can give each worktree its own emulator, KMP and iOS repositories its own iOS simulator, and Xcode repositories see and clean up their DerivedData and share compilations between worktrees. |

It plugs into the tools that run agents rather than replacing them: Claude Code's worktree and session hooks, and [Herdr](https://herdr.dev), the terminal multiplexer for agents, whose worktrees a ShogunAi plugin provisions and where the desktop app opens a worktree with its environment and agent. **Available.**

```mermaid
graph LR
    A["Repository configuration"] --> B["Command-line output for scripts"]
    A --> D["Android, KMP and JVM provisioning"]
```

## Planned

### Desktop app redesign

In progress on the `epic/ui-redesign` branch. The desktop app grew one feature at a time without a design: one project at a time, crowded worktree rows, machine resources at the bottom of a project's list and errors shown as raw command output. The redesign turns it into a control tower built around three panes:

- **Sidebar**: "Active" (the main view: worktrees with an agent working, work waiting for you, or still in progress, across every project), "All worktrees" (every project, where selecting a project filters the view), the projects, and a "Machine" section with Gradle daemons, Android emulators, iOS simulators and Xcode DerivedData across every project.
- **List**: one line per worktree, with the branch as its label and compact indicators for what matters.
- **Detail**: everything about the selected worktree and its actions.

It's planned as these OpenSpec changes, implemented in this order:

1. `command-palette`: Cmd/Ctrl+K to jump to any view, project or worktree, or run an action.

Each change removes its line from this list when it's archived.

### Guided onboarding demo

A first-run walkthrough built on the review done before merging the worktree layer: the app creates a throwaway sample repository with worktrees in every state, and opens an agent with a prepared prompt that walks the user through each feature (status, cleanup, provisioning, the CLI, hooks, environment and providers), one step at a time.

### Repository configuration

Richer provisioning in the repository's `.shogunai.json`:

- A post-creation hook run as argv (e.g. `npm install`, `direnv allow`).
- Symlinking a secret instead of copying it, so a rotated secret updates every worktree.
- Detection of secrets in a worktree that differ from the base repository's.

### Command-line output for scripts

- A machine-readable output (`--json`) for the `shogun` commands that scripts need, starting with `status`.

### Android, KMP and JVM provisioning

Provisioning for the stacks where a fresh worktree is furthest from building:

- `local.properties`, keystores and `google-services.json`.
- "Open in Android Studio" on the right worktree.
- Showing the emulators ShogunAi starts inside Android Studio's Running Devices window, as the ones Android Studio starts itself, so several of them can be arranged in one window. It needs a spike: Android Studio may embed an external emulator started with the options it uses itself.

### Choosing the base of a new worktree

`shogun new` and Claude Code's `WorktreeCreate` hook already start a new branch at the HEAD of the worktree they run in, and `shogun new --from <ref>` at any branch, tag or commit. The desktop app still starts it at the base repository's `HEAD`, so creating a worktree there from an up-to-date branch means going to another Git client first. Still planned:

- Pick the base branch (`develop`, `main`, …) when creating a worktree in the desktop app, with an optional `git fetch` beforehand.
- Create a worktree from a remote branch (`origin/...`), not only from local ones.

### Launching the agent and tools directly

Copying the launch command stays as the default, but the app could also open it directly with reliable, specific integrations instead of a generic terminal launch:

- Terminals: iTerm2 and Terminal on macOS, Windows Terminal, tmux and Zellij.
- Herdr's agent status (working, blocked, done) in each worktree row, read from its API, now that worktrees can be opened in Herdr.
- "Open in IDE" (VS Code, IntelliJ IDEA, Android Studio) and "Open folder".

### Multi-agent mode

Some users switch between AI agents on the same task, for example when one of them runs out of quota. A project would hold several agent commands instead of one (Claude, Antigravity, Codex, …), each with its default arguments, and the user would pick the agent when copying or launching the command. The current single-agent setup stays the default for users who don't need it.

### Desktop ergonomics

- Menu bar / system tray icon with recent projects and worktrees.

### Distribution

- Update check against GitHub Releases: the app notices a newer version and offers to download it (or points to `brew upgrade` when installed through Homebrew).
- Windows package managers: publish to winget and Scoop alongside the `.msi`.

## Under exploration

### Integration with GitHub and task trackers

Creating a worktree from a GitHub issue or a tracker task (deriving the task id and title), and showing the pull request state of each branch (open, in review, CI failing, merged). The value is clear, but the integration model isn't yet: which providers, how to authenticate, and how much to rely on external CLIs such as `gh`. It needs more thought before becoming a change.

## Out of scope for now

### Running agents inside the app

ShogunAi doesn't embed terminals or manage agent sessions: agents and terminals already do that well. It focuses on the worktrees they work in.

### macOS signing and notarization

Left out for now: it requires a paid Apple developer program. Until then, the README explains how to open the unnotarized app.
