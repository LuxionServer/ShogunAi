# Roadmap

Ideas accepted for ShogunAi's future, grouped by theme. This page is a direction, not a commitment to dates: each item becomes an OpenSpec change (`/opsx:propose`) when it's picked up, and its design is decided there. Once a change is archived, its item is removed from this page.

## Direction

ShogunAi is evolving from a desktop app that creates worktrees into **the worktree layer for any AI agent**: it prepares each worktree so it's ready to build, and gives you an overview of all of them, whoever created them. It doesn't embed terminals or run agents itself; Claude, Codex or any other agent keeps doing that, inside the worktrees ShogunAi prepares and oversees.

That direction is built in three layers:

| Layer | Role | What it brings |
|---|---|---|
| **Engine and CLI** | The foundation | Worktree provisioning defined in the repository (`.shogunai.json`) and a `shogun` command that the desktop app, agents and their hooks all use, including a per-worktree environment (reserved ports and variables) delivered to each agent's shell. **Available**; richer provisioning is planned. |
| **Control tower** | The view | The desktop app shows the state of every worktree, including the ones created by other tools, keeps them clean and follows changes as they happen. **Available.** |
| **Android, KMP and JVM** | The content | Provisioning for the stacks where a worktree is hardest to prepare: Gradle, local secrets, emulators and IDEs. **Started**: Gradle repositories show their daemons and can stop the idle ones, and Android repositories can give each worktree its own emulator. |

```mermaid
graph LR
    A["Repository configuration"] --> B["Command-line output for scripts"]
    A --> D["Android, KMP and JVM provisioning"]
```

## Planned

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
- Gradle settings per worktree (daemon memory, idle timeout), so parallel worktrees don't multiply memory usage, delivered through the per-worktree environment.
- An iOS simulator per worktree for KMP projects, through the same provider mechanism as the Android emulator.
- "Open in Android Studio" on the right worktree.

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
