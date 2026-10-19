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
| `env` | object of strings | Per-worktree environment variables. Names use letters, digits and `_`, and don't start with a digit. Names that change how shells, runtimes, the dynamic loader or Git behave are reserved and make the file invalid: `PATH`, `HOME`, `SHELL`, `IFS`, `ENV`, `BASH_ENV`, `ZDOTDIR`, `PROMPT_COMMAND`, `NODE_OPTIONS`, `NODE_PATH`, `PYTHONPATH`, `PYTHONSTARTUP`, `PYTHONHOME`, `RUBYOPT`, `RUBYLIB`, `PERL5OPT`, `PERL5LIB`, `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, `JDK_JAVA_OPTIONS`, `CLASSPATH` and any name starting with `LD_`, `DYLD_` or `GIT_`. A project script can set those itself. |
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

### Approving post-creation commands

Because `.shogunai.json` is committed, whoever controls a branch controls its commands. They therefore run only after you have approved them on this machine, for that repository. The approval covers exactly the list you saw: if a pull changes a command, adds one or reorders them, the list counts as not approved and ShogunAi asks again. Approvals are stored in `~/.shogunai/trust.json`, readable only by you.

- The desktop app shows the commands in the create dialog and offers "Run and remember" or "Skip". Skipping creates the worktree without running them. Project settings lists the repository's commands, whether they are approved on this machine, and lets you approve or revoke them.
- `shogun new` and `shogun provision` print the commands and ask on a terminal. Where they can't ask, and always in the agent hooks, they skip the commands and print a note on standard error that names `shogun trust`.
- `shogun trust` shows the commands and approves them (`--yes` skips the question) or revokes the approval (`--revoke`); see [the CLI reference](cli.md#trust).

`copy` and `link` files need no approval: they are only read from the base repository and, after following symbolic links, only written inside the worktree. A file whose destination resolves outside the worktree, through a symlinked folder from the branch, fails the operation.

Provider options:

| Provider | Options |
|---|---|
| `gradle` | `daemonMaxHeap`, `kotlinDaemonMaxHeap`: a size such as `512m` or `2g` (a number followed by `k`, `m` or `g`). `idleTimeoutMinutes`: a positive whole number, not a string. |
| `emulator` | `avd`: the preferred AVD name. |
| `ios-simulator` | `device`, `runtime`: the preferred device type and runtime names. |
| `xcode` | `compilationCache`: `true` or `false`. |

Unknown keys and unknown providers are ignored. A file that isn't valid JSON, or a known key with the wrong type, makes creating and provisioning worktrees fail with a message naming the file and the problem, and nothing is created.

### Editing it from the app

You don't have to write this file by hand. The project settings have a "Repository configuration" section where you turn providers on with their options, edit the `env` variables (with help on the `${port}`, `${port:key}` and `${worktree}` templates) and set the `envFile` path. Values are checked as you type with the same rules as above. Before the first save that writes the file, the app shows the resulting `.shogunai.json` and reminds you that it belongs to the repository and should be committed.

Saving only changes `providers`, `env`, `envFile` and, with "Move to .shogunai.json", `copy`: every other key, unknown provider and unknown provider option stays as it was (keys keep their order, indentation is normalized to two spaces). The app never writes over a file it couldn't read, or one that changed on disk since the settings were opened: in the first case the section explains the problem, in the second it offers to reload. The settings that apply to this machine only (the AVD to use, headless mode, the simulator device and runtime) stay in their own sections, labeled "This machine only".

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

If `projects.json` or `settings.json` can't be read (for example, it isn't valid JSON), ShogunAi never overwrites it silently: the desktop app shows a notice with the file's path, the `shogun` command fails with one line naming it, and before anything writes a new file the unreadable one is kept next to it as `projects.json.unreadable-<timestamp>` (or `settings.json.unreadable-<timestamp>`). An unreadable `settings.json` makes the app start with the default settings. Writes to both files are serialized between the app and `shogun` with a `projects.json.lock` or `settings.json.lock` file next to them, so two writers never drop each other's change.

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
