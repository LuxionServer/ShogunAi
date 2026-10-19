# Roadmap

Ideas accepted for ShogunAi's future, grouped by theme. This page is a direction, not a commitment to dates: each item becomes an OpenSpec change (`/opsx:propose`) when it's picked up, and its design is decided there. Once a change is archived, its item is removed from this page.

## Direction

ShogunAi is evolving from a desktop app that creates worktrees into **the worktree layer for any AI agent**: it prepares each worktree so it's ready to build, and gives you an overview of all of them, whoever created them. It doesn't embed terminals or run agents itself; Claude, Codex or any other agent keeps doing that, inside the worktrees ShogunAi prepares and oversees.

That direction is built in three layers:

| Layer | Role | What it brings |
|---|---|---|
| **Engine and CLI** | The foundation | Worktree provisioning defined in the repository (`.shogunai.json`) and a `shogun` command that the desktop app, agents and their hooks all use, including a per-worktree environment (reserved ports and variables) delivered to each agent's shell. **Available.** |
| **Control tower** | The view | The desktop app shows the state of every worktree, including the ones created by other tools, keeps them clean and follows changes as they happen. **Available.** |
| **Android, KMP and JVM** | The content | Provisioning for the stacks where a worktree is hardest to prepare: Gradle, local secrets, emulators and IDEs. **Started**: Gradle repositories show their daemons, can stop the idle ones and bound the memory and lifetime of the daemons agents start, Android repositories can give each worktree its own emulator, KMP and iOS repositories its own iOS simulator, and Xcode repositories see and clean up their DerivedData and share compilations between worktrees. |

It plugs into the tools that run agents rather than replacing them: Claude Code's worktree and session hooks, and [Herdr](https://herdr.dev), the terminal multiplexer for agents, whose worktrees a ShogunAi plugin provisions and where the desktop app opens a worktree with its environment and agent. **Available.**

```mermaid
graph LR
    R["Robustness of the engine"] --> P["Command line and desktop parity"]
```

## Planned

### Hardening after real use

A review of the whole project on 2026-10-19, after using it on real repositories, turned into six OpenSpec changes, already proposed in `openspec/changes/`:

- **Robustness of the engine** (`process-robustness`): cancelled commands stop, commands never wait for input, a failed creation removes its branch, corrupted local files are reported and kept, submodules work, and branches are deleted by one rule everywhere; cleanup never removes a worktree with a running agent.
- **Desktop fixes after the redesign** (`desktop-review-fixes`): no crash when a creation races a refresh, errors visible in every grouping, errors inside the removal dialog, and path edits in settings that keep unsaved changes.
- **Background refresh cost** (`background-refresh-cost`): the watcher pauses while the window is in the background and only checks the shown projects, with cheaper device reads (done).
- **Command line and desktop parity** (`cli-desktop-parity`): `shogun remove` and `shogun repair`, and in the app the provisioning state and "Provision" for worktrees created by other tools, plus the worktree's environment variables.
- **Docs and build hygiene** (`docs-hygiene`): missing docs, the Herdr plugin manifest test, compiler warnings, unused strings and dependencies, and ignore rules.

Follow-ups left out of those changes:

- Support for bare repositories with worktrees (the "bare clone" layout), which changes what the base repository and copying from it mean.
- Filesystem watchers instead of polling for Git metadata changes.
- Splitting `ProjectWorktrees.kt` and `ProjectSettingsView.kt` into smaller holders and sections.
- Build provenance attestations for release assets, and treating compiler warnings as errors once the build is clean.

### Guided onboarding demo

A first-run walkthrough built on the review done before merging the worktree layer: the app creates a throwaway sample repository with worktrees in every state, and opens an agent with a prepared prompt that walks the user through each feature (status, cleanup, provisioning, the CLI, hooks, environment and providers), one step at a time.

### Android, KMP and JVM provisioning

Provisioning for the stacks where a fresh worktree is furthest from building:

- `local.properties`, keystores and `google-services.json`.
- "Open in Android Studio" on the right worktree.
- Showing the emulators ShogunAi starts inside Android Studio's Running Devices window, as the ones Android Studio starts itself, so several of them can be arranged in one window. It needs a spike: Android Studio may embed an external emulator started with the options it uses itself.

### Launching the agent and tools directly

Copying the launch command stays as the default, but the app could also open it directly with reliable, specific integrations instead of a generic terminal launch:

- Terminals: iTerm2 and Terminal on macOS, Windows Terminal, tmux and Zellij.
- "Open in IDE" (VS Code, IntelliJ IDEA, Android Studio) and "Open folder".

### Distribution

- Update check against GitHub Releases: the app notices a newer version and offers to download it (or points to `brew upgrade` when installed through Homebrew).

## Under exploration

### Integration with GitHub and task trackers

Creating a worktree from a GitHub issue or a tracker task (deriving the task id and title), and showing the pull request state of each branch (open, in review, CI failing, merged). The value is clear, but the integration model isn't yet: which providers, how to authenticate, and how much to rely on external CLIs such as `gh`. It needs more thought before becoming a change.

## Out of scope for now

### Running agents inside the app

ShogunAi doesn't embed terminals or manage agent sessions: agents and terminals already do that well. It focuses on the worktrees they work in.

### macOS signing and notarization

Left out for now: it requires a paid Apple developer program. Until then, the README explains how to open the unnotarized app.
