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

### Windows (winget or Scoop)

```powershell
winget install LuxionServer.ShogunAi
```

```powershell
scoop bucket add shogunai https://github.com/LuxionServer/scoop-shogunai
scoop install shogunai
```

> [!NOTE]
> Scoop also provides the `shogun` command, without needing Java.

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
- **Scoop**: installed with the app; `shogun --version` works in a new terminal, with no Java needed.
- **Linux (`.deb`)**: the app's launcher is the command. Link it once: `sudo ln -s /opt/shogunai/bin/ShogunAi /usr/local/bin/shogun`.
- **Windows (winget or `.msi`), or without installing the app**: download `shogun-<version>.zip` from the release, unzip it and add its `bin` folder to your `PATH`. It needs Java 21 or later.

## Basic usage

1. **Create a project** — the first time you open the app it offers to add one. Click "Add project" (also in the sidebar and in File, Shift+Cmd/Ctrl+N) and choose the repository folder with the picker, or drop the folder on the window. The name, the worktrees folder next to the repository and the main branch are filled in from it, and you can edit them before adding the project. Optionally add local files that must be copied to each new worktree on this machine.
2. **See what's going on** — the app opens on "Active": the worktrees, across all your projects, with an agent working, with work waiting for you or still in progress, grouped by state. "All worktrees" lists everything, and selecting a project in the sidebar shows its worktrees. Right-click it to edit or remove it; removing a project never touches its repository or worktrees.
3. **Create a worktree** — enter the task id and the branch type (`feature` or `fix`); the app creates the worktree and the branch, and copies the files the repository and the project declare. To work on a branch you already have, switch to "Existing branch" and pick it from the list.
   To share what a worktree needs with everyone who clones the repository, add a `.shogunai.json` at its root with gitignore-style patterns; every file Git doesn't track that matches one is copied into each new worktree, at the same path. A `.worktreeinclude` (Claude Code's file) is read the same way. The "Move to .shogunai.json" button in the project configuration writes your local files into it, and its "Repository configuration" section turns providers on and edits the per-worktree environment without writing the JSON by hand.

   ```json
   { "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"] }
   ```

4. **Start working** — click "Copy command" on a worktree and paste it in your terminal to open your AI agent there.
5. **Remove a worktree** — when you finish a task, remove it from the list, optionally deleting the local branch too. The main worktree can't be removed.

The app is in English by default; you can switch to Spanish in Preferences (Settings… in the ShogunAi menu on macOS, File > Preferences elsewhere, Cmd/Ctrl+Comma), next to the theme. Your choice is remembered between restarts, as are the last project you had open, the window's size and position, and whether the sidebar is collapsed (View > Toggle sidebar, Cmd/Ctrl+Backslash).

### Keyboard shortcuts

Use Cmd on macOS and Ctrl elsewhere.

| Shortcut | Action |
|---|---|
| Cmd/Ctrl+K | Command palette: jump to any view, project, worktree or action |
| Cmd/Ctrl+1, Cmd/Ctrl+2 | Show Active, show All worktrees |
| Cmd/Ctrl+N | New worktree |
| Shift+Cmd/Ctrl+N | Add project |
| Cmd/Ctrl+S | Save the project's settings |
| Cmd/Ctrl+F | Find in the worktree list |
| Shift+Cmd/Ctrl+C | Copy the selected worktree's launch command |
| Cmd/Ctrl+Backspace | Delete the selected worktree (asks first) |
| Cmd/Ctrl+R | Reload |
| Cmd/Ctrl+Backslash | Toggle the sidebar |
| Cmd/Ctrl+Comma | Preferences |

In the command palette, the arrows move through the results, Enter runs the highlighted one and Cmd/Ctrl+Enter copies a worktree's launch command.

## Command line

`shogun` works on the Git repository that contains the current directory, using its project from the desktop app if there is one:

```bash
shogun new TASK-123                # new feature/TASK-123 worktree, provisioned; prints its path
shogun new --branch hotfix/login   # worktree for an existing branch
shogun provision ../other-worktree # copy the declared files into a worktree made by another tool
shogun status                      # every worktree with its changes, divergence and merged state
shogun clean --yes                 # remove merged or missing worktrees (without --yes it only lists them)
eval "$(shogun env)"               # load this worktree's variables from .shogunai.json "env" (ports, …)
```

Run `shogun help` for every option, or see the [command-line reference](./docs/guide/cli.md). Without a project in the app, `shogun new` needs `--worktrees-root <dir>`. Everything `.shogunai.json` accepts is in [Configuration](./docs/guide/configuration.md).

### With AI agents

Make `claude --worktree` create its worktrees through ShogunAi by adding the hooks to `.claude/settings.json`:

```json
{
  "hooks": {
    "WorktreeCreate": [{ "hooks": [{ "type": "command", "command": "shogun hook worktree-create" }] }],
    "WorktreeRemove": [{ "hooks": [{ "type": "command", "command": "shogun hook worktree-remove" }] }],
    "SessionStart": [{ "hooks": [{ "type": "command", "command": "shogun hook session-start" }] }]
  }
}
```

`SessionStart` gives each session its worktree's own variables, such as a dev server port, declared in `.shogunai.json` (`"env": { "PORT": "${port}" }`). Recipes for Herdr, Cursor, Codex, direnv and any other tool are in [Using with agents](./docs/agents.md).

## Providers

A repository turns on providers in its `.shogunai.json` to give each worktree what its stack needs:

```json
{ "providers": { "gradle": { "daemonMaxHeap": "2g" }, "emulator": {}, "ios-simulator": {}, "xcode": { "compilationCache": true } } }
```

- **Gradle**: see and stop the Gradle and Kotlin daemons every worktree build leaves behind, and bound their memory and lifetime.
- **Android emulator**: each worktree gets its own emulator and `ANDROID_SERIAL` the first time it starts one, so parallel agents don't install on each other's devices.
- **iOS simulator** (macOS): each worktree gets its own simulator and `IOS_SIMULATOR_UDID` the first time it starts one, for `xcodebuild` and KMP tests.
- **Xcode** (macOS): see and delete orphaned DerivedData, and share compilations between worktrees.

Emulators, simulators and Gradle daemons share machine-wide pools whose limits follow a capacity profile chosen in Preferences (Automatic from the machine's memory and cores by default): when a pool is full, the least recently used idle device is released to make room.

How to set up each one is in [Providers](./docs/guide/providers.md). Repositories without a provider never see any of this.

## Development

Want to build the project from source or contribute? Start with [`docs/getting-started.md`](./docs/getting-started.md).

## Documentation

The user guide, architecture and design decisions are in [`/docs`](./docs) (English and Spanish), served with MkDocs:

```bash
pip install -r requirements-docs.txt
mkdocs serve
```

## License

[GNU GPLv3](./LICENSE)
