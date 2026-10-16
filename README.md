# ShogunAi

**English** · [Español](./README.es.md)

Desktop orchestrator that automates creating and managing isolated **Git worktrees**, with automatic provisioning of local secrets — designed to remove the manual context switching when you (or an AI agent like Claude/Copilot) work on several tasks at once.

## What it does

- Creates and removes isolated **Git worktrees** for each task, each on its own new branch (`feature/` or `fix/`) or on a branch that already exists locally.
- Automatically copies to each new worktree the **local files** it needs to build (e.g. Android's `local.properties`, `.env` files), as declared in a `.shogunai.json` versioned in the repository or in Claude Code's `.worktreeinclude`.
- Manages several projects from a catalog, each with its own configuration (base repository, worktrees folder, agent command).
- Copies the command to start your AI agent in a worktree (`cd <worktree> && claude`, optionally wrapped with Headroom), ready to paste in any terminal.
- If you move a repository, update its path in the project and the app re-links its worktrees (`git worktree repair`).
- Includes the `shogun` command, to create, provision, inspect and clean worktrees from a terminal, a script or an agent hook.

## Installation

### macOS (Homebrew)

```bash
brew tap LuxionServer/shogunai
brew install --cask shogunai
```

> [!NOTE]
> Homebrew picks the installer for your Mac's architecture (Apple Silicon or Intel) automatically. The app isn't notarized by Apple: if macOS warns that "the developer cannot be verified" the first time you open it, open it with right click → Open, or run `xattr -dr com.apple.quarantine "/Applications/ShogunAi.app"`.

### Manual download

Download the installer for your operating system from the [latest release](https://github.com/LuxionServer/ShogunAi/releases/latest):

| Operating system | Installer |
|---|---|
| macOS (Apple Silicon) | `-arm64.dmg` |
| macOS (Intel) | `-x64.dmg` |
| Windows | `.msi` |
| Linux | `.deb` |

Install it like any other desktop app and open it. You don't need a JDK or any development tools: the installer bundles its own runtime.

### The `shogun` command

- **Homebrew**: installed with the Cask; `shogun --version` works in a new terminal.
- **Linux (`.deb`)**: the app's launcher is the command. Link it once: `sudo ln -s /opt/shogunai/bin/ShogunAi /usr/local/bin/shogun`.
- **Windows, or without installing the app**: download `shogun-<version>.zip` from the release, unzip it and add its `bin` folder to your `PATH`. It needs Java 21 or later.

## Basic usage

1. **Create a project** — the first time you open the app you'll see an empty project catalog. Click "New project" and enter a name, the path of the base repository and the folder where worktrees will be created. Optionally add local files that must be copied to each new worktree on this machine.
2. **Open a project** — select it from the catalog to see its existing worktrees.
3. **Create a worktree** — enter the task id and the branch type (`feature` or `fix`); the app creates the worktree and the branch, and copies the files the repository and the project declare. To work on a branch you already have, switch to "Existing branch" and pick it from the list.
   To share what a worktree needs with everyone who clones the repository, add a `.shogunai.json` at its root with gitignore-style patterns; every file Git doesn't track that matches one is copied into each new worktree, at the same path. A `.worktreeinclude` (Claude Code's file) is read the same way. The "Move to .shogunai.json" button in the project configuration writes your local files into it.

   ```json
   { "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"] }
   ```

4. **Start working** — click "Copy command" on a worktree and paste it in your terminal to open your AI agent there.
5. **Remove a worktree** — when you finish a task, remove it from the list, optionally deleting the local branch too. The main worktree can't be removed.

The app is in English by default; you can switch to Spanish from the language selector in the top bar (next to the theme selector). Your choice is remembered between restarts.

## Command line

`shogun` works on the Git repository that contains the current directory, using its project from the desktop app if there is one:

```bash
shogun new TASK-123                # new feature/TASK-123 worktree, provisioned; prints its path
shogun new --branch hotfix/login   # worktree for an existing branch
shogun provision ../other-worktree # copy the declared files into a worktree made by another tool
shogun status                      # every worktree with its changes, divergence and merged state
shogun clean --yes                 # remove merged or missing worktrees (without --yes it only lists them)
```

Run `shogun help` for every option. Without a project in the app, `shogun new` needs `--worktrees-root <dir>`.

### With AI agents

Make `claude --worktree` create its worktrees through ShogunAi by adding the hooks to `.claude/settings.json`:

```json
{
  "hooks": {
    "WorktreeCreate": [{ "hooks": [{ "type": "command", "command": "shogun hook worktree-create" }] }],
    "WorktreeRemove": [{ "hooks": [{ "type": "command", "command": "shogun hook worktree-remove" }] }]
  }
}
```

Recipes for Cursor, Codex and any other tool are in [Using with agents](./docs/agents.md).

## Development

Want to build the project from source or contribute? Start with [`docs/getting-started.md`](./docs/getting-started.md).

## Documentation

Architecture and design decisions in [`/docs`](./docs) (English and Spanish), served with MkDocs:

```bash
pip install -r requirements-docs.txt
mkdocs serve
```

## License

[GNU GPLv3](./LICENSE)
