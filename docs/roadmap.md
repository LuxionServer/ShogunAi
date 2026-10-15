# Roadmap

Ideas accepted for ShogunAi's future, grouped by theme. This page is a direction, not a commitment to dates: each item becomes an OpenSpec change (`/opsx:propose`) when it's picked up, and its design is decided there. Once a change is archived, its item is removed from this page.

## Planned

### Worktree status at a glance

Today the worktree list only shows each worktree's path and branch. When several agents work in parallel, you need to see which ones are finished and which ones are still dirty without opening a terminal. Each worktree would show:

- Uncommitted changes (`git status --porcelain`).
- Commits ahead of and behind its upstream and the main branch (`git rev-list --count`).
- Date of the last commit.
- Whether its branch is already merged into the main branch.

### Stale worktree cleanup

- One action to remove every worktree whose branch is already merged, optionally deleting the branches too.
- `git worktree prune` for worktrees whose folder no longer exists.

Depends on the worktree status above to know which branches are merged.

### Choosing the base of a new worktree

Today a new branch starts from the base repository's `HEAD`, so creating a worktree from an up-to-date branch means going to another Git client first. Instead:

- Pick the base branch (`develop`, `main`, …) when creating a worktree, with an optional `git fetch` beforehand.
- Create a worktree from a remote branch (`origin/...`), not only from local ones.

### Richer worktree provisioning

Copying secret files is only part of preparing a worktree to build. Each project could also configure:

- A post-creation hook run as argv (e.g. `npm install`, `direnv allow`).
- Folders and glob patterns to copy (e.g. `.env*`, `.idea/runConfigurations`), not only single files.
- Symlinking a secret instead of copying it, so a rotated secret updates every worktree.
- Detection of secrets in a worktree that differ from the base repository's.

### Launching the agent and tools directly

Copying the launch command stays as the default, but the app could also open it directly with reliable, specific integrations instead of a generic terminal launch:

- Terminals: iTerm2 and Terminal on macOS, Windows Terminal, tmux and Zellij.
- "Open in IDE" (VS Code, IntelliJ IDEA, Android Studio) and "Open folder".

### Multi-agent mode

Some users switch between AI agents on the same task, for example when one of them runs out of quota. A project would hold several agent commands instead of one (Claude, Antigravity, Codex, …), each with its default arguments, and the user would pick the agent when copying or launching the command. The current single-agent setup stays the default for users who don't need it.

### Desktop ergonomics

- Menu bar / system tray icon with recent projects and worktrees.
- Command palette (`⌘K` / `Ctrl+K`) to jump to any project or worktree.
- Automatic refresh by watching the repository's worktrees, instead of reloading by hand.
- Import and export of a project's configuration without absolute paths, e.g. a `.shogunai.json` in the repository that defines its secrets and hooks, so a team can share it.

### Distribution

- Update check against GitHub Releases: the app notices a newer version and offers to download it (or points to `brew upgrade` when installed through Homebrew).
- Windows package managers: publish to winget and Scoop alongside the `.msi`.

## Under exploration

### Integration with GitHub and task trackers

Creating a worktree from a GitHub issue or a tracker task (deriving the task id and title), and showing the pull request state of each branch (open, in review, CI failing, merged). The value is clear, but the integration model isn't yet: which providers, how to authenticate, and how much to rely on external CLIs such as `gh`. It needs more thought before becoming a change.

## Out of scope for now

### macOS signing and notarization

Left out for now: it requires a paid Apple developer program. Until then, the README explains how to open the unnotarized app.
