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

```mermaid
graph LR
    A["Repository configuration"] --> B["Command-line output for scripts"]
    A --> D["Android, KMP and JVM provisioning"]
```

## Planned

### Worktree screen redesign

With every provider on, a worktree row now carries status, origin, agents, size, ports, emulator and simulator, and the screen also holds the Gradle daemon and DerivedData sections. A redesign of the screen:

- Rows that don't crowd or cut their buttons, with the branch as the main label and the folder as secondary information.
- Emulator and simulator lines only where they're relevant (running, or on demand), instead of on every row of a repository that declares the provider.
- A better place for the Gradle daemons and DerivedData sections than the bottom of the worktree list.
- A direct way to edit the project from its worktree screen.

### Providers configuration screen

Providers, `env` and `envFile` are declared by editing `.shogunai.json` by hand, which few users will do. A configuration screen to turn providers on and set their options (Gradle heap and idle timeout, preferred AVD, simulator device, Xcode compilation cache) and the per-worktree environment, writing the repository's file like the local files move already does.

### Understandable errors

Errors are often shown as raw command output (a Git or `simctl` message with its exit code). Each error should say in plain words what failed and what to do next, keeping the raw output available for those who need it.

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

Today a new branch starts from the base repository's `HEAD`, so creating a worktree from an up-to-date branch means going to another Git client first. Instead:

- Pick the base branch (`develop`, `main`, …) when creating a worktree, with an optional `git fetch` beforehand.
- Create a worktree from a remote branch (`origin/...`), not only from local ones.

### Launching the agent and tools directly

Copying the launch command stays as the default, but the app could also open it directly with reliable, specific integrations instead of a generic terminal launch:

- Terminals: iTerm2 and Terminal on macOS, Windows Terminal, tmux and Zellij.
- "Open in IDE" (VS Code, IntelliJ IDEA, Android Studio) and "Open folder".

### Multi-agent mode

Some users switch between AI agents on the same task, for example when one of them runs out of quota. A project would hold several agent commands instead of one (Claude, Antigravity, Codex, …), each with its default arguments, and the user would pick the agent when copying or launching the command. The current single-agent setup stays the default for users who don't need it.

### Desktop ergonomics

- Menu bar / system tray icon with recent projects and worktrees.
- Command palette (`⌘K` / `Ctrl+K`) to jump to any project or worktree.

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
