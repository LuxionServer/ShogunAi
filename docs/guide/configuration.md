# Configuration

ShogunAi reads two kinds of configuration: what a **repository** declares for everyone who clones it, versioned in the repository, and what **this machine** stores for you under `~/.shogunai/`.

## Repository configuration (`.shogunai.json`)

A `.shogunai.json` at the repository's root declares what every worktree needs. Every key is optional, and a blank file counts as `{}`:

```json
{
  "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"],
  "link": ["/keys/"],
  "hooks": { "postCreate": [["npm", "install"], ["direnv", "allow"]] },
  "env": {
    "PORT": "${port}",
    "API_URL": "http://localhost:${port:api}"
  },
  "envFile": ".env.shogunai",
  "providers": {
    "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30 },
    "emulator": { "avd": "Pixel_8_API_35" }
  }
}
```

| Key | Type | Meaning |
|---|---|---|
| `copy` | list of strings | Gitignore-style patterns. Every file Git doesn't track that matches one is copied into each new worktree, at the same path. |
| `link` | list of strings | The same patterns as `copy`, but each matching file is linked with a symbolic link to the base repository's file instead of copied, so rotating it there updates every worktree. A file matching both lists is linked. Where symbolic links can't be created (Windows without the privilege), the file is copied instead. |
| `hooks.postCreate` | list of commands | Commands run inside each worktree after it is created or provisioned, once its files are in place and its environment prepared. Each command is a list of strings given as argv, not a shell string; a single list of strings is read as one command. |
| `env` | object of strings | Per-worktree environment variables. Names use letters, digits and `_`, and don't start with a digit. |
| `envFile` | string | A path inside the worktree where its environment is written in dotenv format. Never written over a file Git tracks, so add it to `.gitignore`. |
| `providers` | object | The [providers](providers.md) the repository uses, each with its own options. |

Values in `env` can use these templates:

| Template | Becomes |
|---|---|
| `${port}` | A port reserved for that variable. |
| `${port:<key>}` | A port shared by every variable using the same key. |
| `${worktree}` | The worktree's folder name. |
| `$$` | A literal `$`. |

Ports are unique among the repository's worktrees, free when reserved, and never change afterwards. [Using with agents](../agents.md#per-worktree-environment) explains how agents and shells get these variables.

Linking shares the file: a tool in one worktree that overwrites a linked secret overwrites the base repository's too. Keep `copy` for files a worktree may change. The project's local files are always copied.

Post-creation commands run in order and stop at the first one that fails. A failure fails creation or provisioning with an error naming the command, its exit code and its error output, but the worktree is kept, since its checkout and files are fine. The commands run on creation and on every `shogun provision`, so make them safe to repeat, and only declare commands you would run from a repository you trust. They don't receive the worktree's environment variables; a command that needs them can run `shogun env`.

Provider options:

| Provider | Options |
|---|---|
| `gradle` | `daemonMaxHeap`, `kotlinDaemonMaxHeap`: a size such as `512m` or `2g` (a number followed by `k`, `m` or `g`). `idleTimeoutMinutes`: a positive whole number, not a string. |
| `emulator` | `avd`: the preferred AVD name. |
| `ios-simulator` | `device`, `runtime`: the preferred device type and runtime names. |
| `xcode` | `compilationCache`: `true` or `false`. |

Unknown keys and unknown providers are ignored. A file that isn't valid JSON, or a known key with the wrong type, makes creating and provisioning worktrees fail with a message naming the file and the problem, and nothing is created.

## `.worktreeinclude`

Claude Code's `.worktreeinclude` is read like `copy`: one gitignore-style pattern per line, skipping blank lines and lines starting with `#`. Both files can be used together.

## Machine files (`~/.shogunai/`)

| File | Contents |
|---|---|
| `projects.json` | The desktop app's project catalog: each project's name, base repository, worktrees folder, main branch, local files, agent command, and emulator and simulator settings. The `shogun` command reads it to find a repository's project. |
| `settings.json` | The app's theme (`LIGHT`, `DARK` or `SYSTEM`, the default) and language (`ENGLISH`, the default, or `SPANISH`). |
| `reservations.json`, `reservations.lock` | An index of the worktrees, in any repository, that have reserved ports, and the lock taken while reserving, so two worktrees never get the same port. |
| `logs/emulator-<port>.log` | The output of each emulator ShogunAi started. |
| `xcode/compilation-cache.xcconfig` | The shared Xcode settings written by the Xcode provider's `compilationCache`. |

These files contain paths of your machine; don't commit them anywhere.

## Per-worktree state

Each worktree's reserved ports and resources (its iOS simulator, for instance) are kept in `shogunai-env.json` inside Git's own folder for that worktree (`.git/worktrees/<name>/`), not in the worktree itself. Removing the worktree removes them with it.

## Other locations

- `$GRADLE_USER_HOME/init.d/shogunai-ios-simulator.init.gradle.kts` (`~/.gradle/init.d/` by default): the init script installed by `shogun simulator gradle-init install`.
- Herdr's `config.toml` (`$XDG_CONFIG_HOME/herdr/`, or `~/.config/herdr/`): ShogunAi only reads its `[worktrees] directory`, to recognize worktrees created from Herdr (`~/.herdr/worktrees` when unset).

## Port ranges

| Use | Range |
|---|---|
| `${port}` and `${port:<key>}` | 20000–29999 |
| Android emulator consoles | 5554–5584, even numbers only |
