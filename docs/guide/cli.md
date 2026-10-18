# Command-line reference

`shogun` runs ShogunAi's worktree operations from a terminal, a script or an agent hook. It works on the Git repository that contains the current directory (the main checkout, a subfolder or any of its worktrees), and uses that repository's project from the desktop app when there is one: its worktrees folder, local files and main branch. See [Installation](installation.md#the-shogun-command) to put it on your `PATH`.

```bash
shogun new TASK-123                # new feature/TASK-123 worktree, provisioned; prints its path
shogun new --branch hotfix/login   # worktree for an existing branch
shogun provision ../other-worktree # copy the declared files into a worktree made by another tool
shogun status                      # every worktree with its changes, divergence and merged state
shogun status --json               # the same, as one JSON document for scripts
shogun clean --yes                 # remove merged or missing worktrees (without --yes it only lists them)
eval "$(shogun env)"               # load this worktree's variables from .shogunai.json "env"
```

Output is plain English text; scripts should read `new`'s single line and `status --json`.

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Success. |
| `1` | The operation failed; the reason is on standard error. |
| `2` | Wrong usage: unknown command or option, missing argument, or hook input that isn't the expected JSON. |

## Worktrees

### `new`

```text
shogun new <task-id> [--fix] [--from <ref>] [--worktrees-root <dir>]
shogun new --branch <branch> [--worktrees-root <dir>]
```

Creates a worktree, copies the declared files into it, prepares its [environment](../agents.md#per-worktree-environment) and prints its absolute path, alone on standard output, so `cd "$(shogun new TASK-123)"` works.

- With a task id, the worktree gets a new `feature/<task-id>` branch, or `fix/<task-id>` with `--fix`. The branch starts at the HEAD of the worktree you run the command in, or at `--from <ref>` (a branch, tag or commit; an unknown ref fails before anything is created).
- With `--branch`, the worktree checks out an existing local branch. `--fix` and `--from` aren't accepted with it.
- `--worktrees-root <dir>` sets where the worktree goes. It's required when the repository has no project in the desktop app; relative paths are resolved against the current directory.

### `provision`

```text
shogun provision [<path>]
```

Copies the project's local files and the repository's declared files into an existing worktree (the current directory by default), reserves its ports and writes its `envFile`. Use it for worktrees created by another tool. Prints one copied path per line.

### `status`

Lists every worktree of the repository, one per line: path, branch (or `(detached)`), then details:

| Detail | Meaning |
|---|---|
| `main` | The main checkout. |
| `origin=shogunai\|claude-code\|herdr\|other` | Where the worktree was created. |
| `locked` | Held by an agent session. |
| `changes=N` | Uncommitted changes. |
| `ahead=N behind=N` | Divergence from its upstream. |
| `merged` | Merged into the main branch. |
| `folder-missing` | The folder was deleted by hand. |
| `status-unavailable` | Its status couldn't be read. |

#### `status --json`

```text
shogun status [--json]
```

Prints one compact JSON object on a single line instead of the text lines. Errors still go to standard error as text, with nothing on standard output. For example (wrapped here):

```json
{
  "repository": "/home/dev/projects/main-repo",
  "mainBranch": "main",
  "worktrees": [
    {
      "path": "/home/dev/projects/main-repo",
      "branch": "main",
      "head": "4f1c2e9a0b7d3c58e1f6a2b94d07c3e5a8f1b620",
      "main": true,
      "locked": false,
      "folderMissing": false,
      "origin": null,
      "status": {
        "uncommittedChanges": 0,
        "upstream": { "ahead": 0, "behind": 0 },
        "mainBranch": null,
        "merged": false,
        "lastCommitAt": "2026-10-18T09:12:44Z"
      }
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `repository`, `mainBranch` | The repository's path and its resolved main branch (`null` if it can't be resolved). |
| `branch`, `head` | The checked-out branch (`null` when detached) and the commit. |
| `main`, `locked`, `folderMissing` | The main checkout, held by an agent session, folder deleted by hand. |
| `origin` | `shogunai`, `claude-code`, `herdr` or `other`; `null` for the main checkout. |
| `status` | `null` when the folder is missing or its status couldn't be read. |
| `status.uncommittedChanges`, `status.merged` | Uncommitted changes; merged into the main branch. |
| `status.upstream`, `status.mainBranch` | `{ "ahead": n, "behind": n }` against the upstream and the main branch; `null` when there is none. |
| `status.lastCommitAt` | Time of the last commit in ISO-8601 UTC, or `null`. |

Every field is always present, with `null` when it doesn't apply. The field names are a contract: later versions only add fields and never rename, remove or change the type of one.

### `clean`

```text
shogun clean [--yes|-y] [--delete-branches] [--stop-gradle-daemons] [--stop-emulators]
             [--delete-simulators] [--delete-derived-data]
```

Selects the same worktrees as the desktop app's "Clean up": not the main one, not locked, and either merged with no uncommitted changes or with their folder gone. Without `--yes` it only lists what it would do (`would remove …`); with `--yes` it does it (`removed …`). With nothing to do it prints `Nothing to clean up.`

| Option | Also… |
|---|---|
| `--delete-branches` | Deletes the removed worktrees' branches if they're merged (`kept branch …` otherwise). |
| `--stop-gradle-daemons` | Stops the idle Gradle daemons whose last build was in a removed worktree. |
| `--stop-emulators` | Stops the removed worktrees' Android emulators. |
| `--delete-simulators` | Deletes the removed worktrees' iOS simulators and orphaned ShogunAi simulators. |
| `--delete-derived-data` | Deletes orphaned Xcode DerivedData, the removed worktrees' folders included. |

The Herdr workspaces of removed worktrees are closed too (`would close Herdr workspace of …` in the listing). A daemon, emulator or Herdr workspace that can't be stopped or closed is reported on standard error without changing the exit code.

## Environment

### `env`

```text
shogun env [<path>] [--format sh|dotenv|json]
```

Prints the environment of the worktree containing the path (or the current directory), from `.shogunai.json` `env` and the providers, reserving its ports the first time. `sh` (the default) prints `export NAME='value'` lines for `eval "$(shogun env)"`; `dotenv` and `json` suit other tools. An empty environment prints nothing.

## Providers

These commands act on the [providers](providers.md) a repository declares in `.shogunai.json`. Where a command needs a provider the repository doesn't declare, it says so and exits with `1`.

### `gradle`

```text
shogun gradle
shogun gradle stop [<pid>...]
```

`gradle` lists the Gradle and Kotlin daemons on the machine: pid, kind and version, `memory=`, `state=busy|idle|unknown` (with `idle-for=`) and `worktree=<path>|removed|other-project`, then the total (`No Gradle or Kotlin daemons running.` when there are none). It works outside a repository too. Inside a Gradle repository without Gradle's build cache, it ends with a notice.

`gradle stop` stops every idle daemon, or only the given process ids; busy and unknown daemons are kept (`kept <pid> … (<reason>)`). A pid that isn't a daemon exits with `1`.

### `emulator`

```text
shogun emulator start|stop|status [<path>] [--avd <name>] [--no-window]
```

Starts (and waits for), stops or shows the Android emulator of the worktree. `--avd` and `--no-window` only apply to `start` and override the project's settings for that start. `status` prints `emulator-5556 running|booting|stopped`.

### `simulator`

```text
shogun simulator start|stop|status [<path>] [--device <type>] [--runtime <runtime>]
shogun simulator gradle-init install|uninstall [--yes|-y]
```

Creates if needed, boots (and waits for) and opens, shuts down or shows the iOS simulator of the worktree (macOS only). `--device` and `--runtime` only apply when `start` creates the simulator. In a Kotlin Multiplatform repository, `status` also says whether the KMP tests use the simulator.

`gradle-init install` installs a machine-wide Gradle init script that makes every project's KMP simulator tests use the worktree's simulator; it shows the script and asks `[y/N]` unless `--yes` is given. `uninstall` removes it.

### `xcode`

```text
shogun xcode
shogun xcode clean
```

`xcode` lists the DerivedData folders of the repository's worktrees with their size, the orphaned folders on the machine (`orphans: N (size)`) and, when compilation caching is declared, the cache size. `xcode clean` deletes the orphaned folders (`deleted <path>`, `freed <size>`, or `Nothing to delete.`). Without the Xcode provider, or off macOS, it says the provider isn't active and exits with `0`.

## Agent hooks

These commands are meant to be called by agents and tools, not typed. How to configure them is in [Using with agents](../agents.md).

| Command | Called by |
|---|---|
| `shogun hook worktree-create` | Claude Code's `WorktreeCreate` hook. Reads `name` and `cwd` from standard input, creates (or reuses) a provisioned worktree on `feature/<name>` starting at the HEAD of the worktree in `cwd`, and prints its path. |
| `shogun hook worktree-remove` | Claude Code's `WorktreeRemove` hook. Removes the worktree in `worktree_path`, deletes its branch only if merged and closes its Herdr workspace. |
| `shogun hook session-start` | Claude Code's `SessionStart` hook. Appends the worktree's environment to `CLAUDE_ENV_FILE` and tells Claude which values it uses. |
| `shogun hook herdr` | The ShogunAi plugin for Herdr, on `worktree.created` (provisions the worktree) and `worktree.removed` (deletes its branch if merged). Reads `HERDR_PLUGIN_EVENT`, `HERDR_PLUGIN_EVENT_JSON` and `HERDR_BIN_PATH`. |

## Help and version

| Command | Prints |
|---|---|
| `shogun help`, `-h`, `--help` | The usage summary. |
| `shogun version`, `-v`, `--version` | `shogun <version>`, the same version as the app. |
