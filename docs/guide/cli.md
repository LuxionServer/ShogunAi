# Command-line reference

`shogun` runs ShogunAi's worktree operations from a terminal, a script or an agent hook. It works on the Git repository that contains the current directory (the main checkout, a subfolder or any of its worktrees), and uses that repository's project from the desktop app when there is one: its worktrees folder, local files and main branch. See [Installation](installation.md#the-shogun-command) to put it on your `PATH`.

```bash
shogun new TASK-123                # new feature/TASK-123 worktree, provisioned; prints its path
shogun new --branch hotfix/login   # worktree for an existing branch
shogun provision ../other-worktree # copy the declared files into a worktree made by another tool
shogun status                      # every worktree with its changes, divergence and merged state
shogun status --json               # the same, as one JSON document for scripts
shogun base                        # the branch this worktree's branch was created from, its pull request's base
shogun remove TASK-123             # remove one worktree and stop its emulator and simulator
shogun repair                      # re-link the worktrees after the repository moved
shogun open TASK-123 --herdr       # open a worktree in Herdr with its environment and the project's agent
shogun clean --yes                 # remove merged or missing worktrees (without --yes it only lists them)
shogun stacks                      # the stacks detected in the repository and the providers they suggest
shogun multirepo show              # in a multirepo: its name, repositories, defaults and agent
shogun project add                 # register this repository as a project of the desktop app
shogun app --settings              # open the desktop app on this repository's project settings
eval "$(shogun env)"               # load this worktree's variables from .shogunai.json "env"
```

Output is plain English text; scripts should use `--json`, which every command that reports a result accepts (see [JSON output](#json-output)).

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Success. |
| `1` | The operation failed; the reason is on standard error. Failures a command doesn't handle specifically are printed as one line starting with `shogun:`, never as a stack trace. |
| `2` | Wrong usage: unknown command or option, missing argument, or hook input that isn't the expected JSON. |
| `3` | `provision --check` found differences between the worktree and the base repository. |
| `130` | The command was interrupted (Ctrl+C or a termination signal) and stopped cleanly, e.g. leaving the line of a `start --wait`. |

## Worktrees

### `new`

```text
shogun new <task-id> [--fix] [--from <ref>] [--fetch] [--worktrees-root <dir>]
shogun new --branch <branch> [--fetch] [--worktrees-root <dir>]
```

Creates a worktree, copies the declared files into it, prepares its [environment](../agents.md#per-worktree-environment) and prints its absolute path, alone on standard output, so `cd "$(shogun new TASK-123)"` works.

- With a task id, the worktree gets a new `feature/<task-id>` branch, or `fix/<task-id>` with `--fix`. The branch starts at the HEAD of the worktree you run the command in, or at `--from <ref>` (a branch, tag or commit; an unknown ref fails before anything is created).
- With `--branch`, the worktree checks out an existing branch. `--fix` and `--from` aren't accepted with it. A branch that exists only on `origin`, given as `<name>` or `origin/<name>`, gets a local branch of that name tracking it; a local branch with that name takes precedence.
- A new branch never tracks its start point, so pushing it never targets the branch it started from.
- The branch it starts from is recorded as the new branch's [base](#base): the current worktree's branch, or the branch `--from` names (`origin/develop` is recorded as `develop`). A tag, a commit or a detached HEAD records nothing, and `--branch` keeps whatever an existing branch already has.
- `--fetch` runs `git fetch --prune origin` first, so `--from origin/develop` or a remote branch is current. It never waits for credentials: if the fetch fails, Git's error is printed, nothing is created and the exit code is 1.
- `--worktrees-root <dir>` sets where the worktree goes. It's required when the repository has no project in the desktop app; relative paths are resolved against the current directory.
- A task id containing `/` (`team/TASK-1`) keeps the `/` in the branch (`feature/team/TASK-1`) and becomes one folder, `team-TASK-1`.
- If creating the worktree fails after Git created it or its new branch, both are removed, so retrying with the same task id works. A branch that existed before is never deleted.
- It works in submodules and in repositories whose Git folder is separate from the working tree. In a bare repository it fails saying bare repositories aren't supported yet, and in a repository without commits it fails asking for a first commit.
- When Git fails for a cause it recognizes (branch already exists, lock file present, permission denied…), a `hint:` line after Git's message explains it and suggests what to do, for example `shogun new --branch feature/TASK-123`.

### `provision`

```text
shogun provision [<path>]
shogun provision --check [<path>]
```

Copies the project's local files and the repository's declared `copy` files into an existing worktree (the current directory by default), links its `link` files, reserves its ports, writes its `envFile` and runs the `hooks.postCreate` commands. Use it for worktrees created by another tool. Prints one path per line: linked files end in `(linked)`, and files declared in `link` that were copied because symbolic links aren't available end in `(copied: symbolic links aren't available)`. Then it lists the files it removed because they were deleted from their local folder in the base repository, ending in `(removed: deleted from the base folder)`, and the symbolic links it skipped inside local folders, ending in `(skipped: symbolic link)`. `shogun new` and the `worktree-create` hook note the skipped links on standard error.

With `--check` nothing is changed: it compares the files provisioning would give the worktree with the base repository's and prints one line per difference, `<path>: missing`, `<path>: differs` (a copy whose content changed in the base repository) or `<path>: not linked` (a `link` file that isn't a symbolic link to the base repository's file) or `<path>: deleted in base` (a file copied from a local folder that the base folder no longer has; the next `shogun provision` removes it). It exits with 0 when everything matches, 3 when there is any difference, and 1 on an error.

Post-creation commands run, and the variables the repository declares in `env` and its `envFile` apply, only when the repository is approved on this machine (see [Approving a repository](configuration.md#approving-a-repository)). On a terminal `shogun new` and `shogun provision` print the commands, the declared variables (`NAME = template`) and the environment file before preparing the worktree's environment, and ask `Approve them on this machine? [y/N]` on standard error; only an explicit yes approves and applies them. Without a terminal they skip them, print `shogun: skipped N post-creation commands not approved on this machine; run 'shogun trust' in <repo> to review them` and `shogun: skipped environment variables not approved on this machine (PORT, …); run 'shogun trust' in <repo> to review them` on standard error and succeed.

#### When a post-creation command fails

The worktree is kept. `shogun new` prints its path on standard output, the failure (command, exit code and error output) on standard error, and exits with 1. `shogun provision` prints the failure on standard error and exits with 1.

### `trust`

```text
shogun trust [<path>] [--yes] [--revoke]
```

Prints what the repository asks ShogunAi to run or apply, and whether it is approved on this machine: its post-creation commands, one per line, then `Environment variables declared by <repo>:` with one `NAME = template` line per variable, and `Environment file: <path>`. On a terminal it asks to approve them; `--yes` approves without asking, so scripts and CI can do it, and `--revoke` removes the repository's approval. It says so when the repository declares no commands, variables nor environment file. An agent reading a note about variables awaiting approval should ask you to run it rather than run it itself. Exit code 0 unless something fails.

### `status`

Lists every worktree of the repository, one per line: path, branch (or `(detached)`), then details:

| Detail | Meaning |
|---|---|
| `main` | The main checkout. |
| `origin=shogunai\|claude-code\|herdr\|other` | Where the worktree was created. |
| `base=develop` | The recorded [base](#base) of its branch. |
| `locked` | Held by an agent session. |
| `desktop(until 14:10)`, `desktop(waiting 1/2)` | It holds the [desktop turn](#desktop) until that time, or waits for it in that place of the line. |
| `agents=claude(blocked, app-task-123),codex` | Agents running in it, with their state (`working`, `blocked`, `done`, `idle`) when Herdr reports it or, for Claude, when its [hooks](../agents.md#agent-states-outside-herdr) do. A Claude agent also lists, after its state, the names of its live sessions whose folder is in that worktree, as Claude Code's session registry has them (`claude(app-task-123)` when there's no state). |
| `changes=N` | Uncommitted changes. |
| `ahead=N behind=N` | Divergence from its upstream. |
| `merged` | Merged into the main branch. |
| `not-started` | The branch never had commits of its own (shown instead of `merged`). |
| `folder-missing` | The folder was deleted by hand. |
| `status-unavailable (reason)` | Its status couldn't be read, with Git's reason. |

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
      "base": null,
      "agents": [{ "name": "claude", "state": "blocked", "stateSource": "claude-code", "herdrState": null, "sessions": [{ "name": "main-repo-task-123", "pid": 4242 }] }],
      "status": {
        "uncommittedChanges": 0,
        "upstream": { "ahead": 0, "behind": 0 },
        "mainBranch": null,
        "merged": false,
        "lastCommitAt": "2026-10-18T09:12:44Z",
        "createdAt": "2026-10-17T16:40:02Z"
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
| `base` | The recorded [base](#base) of its branch; `null` when none is recorded or it's detached. |
| `agents` | The agents running in it: `name`, `state` (`working`, `blocked`, `done`, `idle`; `null` when nothing reports one), `stateSource` (`herdr`, `claude-code` or `null`), `herdrState`, the state when Herdr is the source and `null` otherwise, and `sessions`, the agent's live Claude Code sessions in that worktree (`name` and `pid`; empty for other agents or when none is found). |
| `status` | `null` when the folder is missing or its status couldn't be read. |
| `statusProblem` | Only present when the status couldn't be read: Git's reason. |
| `status.uncommittedChanges`, `status.merged` | Uncommitted changes; merged into the main branch. |
| `status.upstream`, `status.mainBranch` | `{ "ahead": n, "behind": n }` against the upstream and the main branch; `null` when there is none. |
| `status.lastCommitAt` | Time of the last commit in ISO-8601 UTC, or `null`. |
| `desktop` | `null`, or the worktree's [desktop turn](#desktop): `{ "role": "holder", "since", "expiresAt" }` when it holds it, `{ "role": "waiting", "position", "since" }` when it waits for it, with ISO-8601 UTC times. |
| `status.createdAt` | Time the worktree was created, in ISO-8601 UTC, read from Git's administrative folder for it; `null` for the main checkout or when it can't be read. |

Every field is always present, with `null` when it doesn't apply. The field names are a contract: later versions only add fields and never rename, remove or change the type of one.

### `base`

```text
shogun base [<task id | path | branch>] [--set <branch>] [--json]
```

Prints the branch a worktree's branch was created from, alone on standard output, so `gh pr create --base "$(shogun base)"` opens its pull request against the right branch. Without a target it's the worktree containing the current directory.

- ShogunAi records it when it creates a branch: `shogun new`, Claude Code's `WorktreeCreate` hook, the desktop app's "New worktree" and the worktrees of a [multirepo group](#multirepos), which start from each repository's main branch.
- It's stored in the repository's Git config as `branch.<branch>.gh-merge-base`, the key `gh pr create` already reads as its default base. Nothing goes into the working tree; every worktree of the repository sees it, renaming the branch keeps it and deleting the branch removes it.
- When none is recorded (a worktree created by Herdr or `git worktree add`, or a branch from before ShogunAi recorded bases), it says so on standard error, suggests `--set` and exits with `1`. A detached worktree has no branch and also exits with `1`.
- `--set <branch>` records it, replacing any previous value. The value must be a local branch or a branch of a remote, whose remote prefix is dropped (`origin/develop` is recorded as `develop`); a tag or an unknown name records nothing and exits with `1`.
- `--json` prints `{ "path", "branch", "base" }`, with `base` `null` (and exit code `1`) when none is recorded.

### `hooks`

```text
shogun hooks install   [--project] [--yes] [--json]
shogun hooks uninstall [--project] [--yes] [--json]
shogun hooks status    [--json]
```

Manages ShogunAi's [Claude Code hooks](../agents.md#claude-code) in Claude Code's settings file: `$CLAUDE_CONFIG_DIR/settings.json`, or `~/.claude/settings.json`; with `--project`, the repository's `.claude/settings.json`.

- `install` adds the `WorktreeCreate`, `WorktreeRemove`, `SessionStart`, `UserPromptSubmit`, `Notification`, `Stop` and `SessionEnd` hooks, keeping every other setting and hook. A hook is ShogunAi's when its command starts with `shogun hook `; installing again adds the missing ones, replaces the ones that differ and never duplicates. It prints what it will add (`+`), replace (`~`) or remove (`-`) and asks `[y/N]` unless `--yes` is given; answering no writes nothing and exits with `0`.
- `uninstall` removes every ShogunAi hook, keeps everything else, and drops an event left empty.
- `status` says whether the hooks are `installed`, `outdated` (naming the events that are missing or different) or `missing`, for the user settings and, inside a repository, for the repository's.
- A settings file that isn't valid JSON is never written: the command prints its path and the problem on standard error and exits with `1`.
- With `--json` nothing is asked: `install` and `uninstall` write only with `--yes`, and print `path`, `applied` and the `added`, `replaced` and `removed` hooks. `status --json` prints `user` and `project` (`null` outside a repository), each with `path`, `state` and `events`.

### `guide`

```text
shogun guide [<topic>] [--plain]
shogun guide --migrate [--file <path>]
shogun guide install   (--agent codex|gemini | --project | --file <path>) [--yes] [--json]
shogun guide uninstall (--agent codex|gemini | --project | --file <path>) [--yes] [--json]
shogun guide status    [--file <path>] [--json]
```

ShogunAi's workflow rules for agents, in the topics `worktrees`, `parallel`, `resources` and `cleanup`. See [Using with agents](../agents.md#shogunais-workflow-rules).

- `guide [<topic>]` prints the first line (the rules are defaults; the project's `AGENTS.md` and the person's instructions take precedence) and then the rules of the topic, or of every topic, that apply in the current directory: main checkout or worktree, whether Herdr is available and which providers the repository declares. The rules that don't apply follow under "Not applicable here:", each with the condition it needs. An unknown topic is a usage error (exit `2`) listing the topics; outside a Git repository it exits with `1` and points to `--plain`.
- `--plain` prints every rule of the requested topics with its condition, without looking at the current directory, so it works anywhere.
- `--migrate` prints every rule with its id and an instruction for an agent: compare the given file with the rules, list what it repeats, propose removing it and show the diff without applying anything until the person confirms. It doesn't read or write the file.
- `install` writes a block that says to run `shogun guide` between `<!-- shogun:guide v1 -->` and `<!-- /shogun:guide -->` into a file chosen with `--agent` (`codex`: `$CODEX_HOME/AGENTS.md`, or `~/.codex/AGENTS.md`; `gemini`: `~/.gemini/GEMINI.md`), `--project` (the repository's `AGENTS.md`) or `--file`; one of them is required, and Cursor has no user-level file, so `--agent cursor` is a usage error. It creates the file when missing, keeps every other byte, never duplicates the block, replaces one from an older version, prints what it will write and asks `[y/N]` unless `--yes` is given; answering no writes nothing and exits with `0`.
- `uninstall` removes the block and the line break `install` added from the same targets, leaving everything else.
- `status` says for the Codex and Gemini files, the repository's `AGENTS.md` (inside a repository) and `--file` whether the block is `installed`, `outdated` or `missing`.
- `install` and `status` warn on standard error when a file mentions `shogun new`, `shogun open` or `shogun clean` outside the block, suggesting `guide --migrate`, and `status` warns when Claude Code's `SessionStart` hook is installed and the repository's `AGENTS.md` or the `--file` also has the block, since Claude Code would get the rules twice. Warnings never change a file.
- With `--json`, `install` and `uninstall` ask nothing and write only with `--yes`, and print `path`, `applied`, `state` and `warnings`; `status --json` prints `targets` (`{ target, path, state, duplicates }`) and `warnings`.

### `remove`

```text
shogun remove <task id | path | branch> [--force|-f] [--delete-branch | --force-delete-branch]
```

Removes one linked worktree, the way the desktop app's remove does. The target is, in this order, the path of a worktree, the exact name of its branch, or a task id (the worktree at `<worktrees folder>/<task id>`); no match, or more than one, removes nothing and fails (listing the candidates). The main worktree is never removed.

Without `--force` it refuses a worktree with uncommitted or untracked changes, or one where an agent is running (`shogun: claude is running in <path>; use --force to remove it anyway`), and exits with `1`. With `--force` it removes it anyway. The branch is kept unless you pass `--delete-branch`, which deletes it only when it's merged into the project's main branch (`deleted branch …`, or `kept branch … (not merged into the main branch)`; a kept branch doesn't make the command fail). `--force-delete-branch` deletes it even if it isn't merged.

After removing it, the command stops what the repository's providers keep for that worktree: its Android emulator (`stopped emulator …`), its iOS simulator (`stopped simulator …`) and the idle Gradle daemons whose last build was in it (`stopped Gradle daemon …`). A resource that can't be stopped is reported on standard error and the exit code is `1`; the worktree is already removed by then. Its Herdr workspace is closed too. Its headless desktop app, if running, is stopped before the folder is removed. If Git can't delete the folder because a program is using it (typically a Herdr shell on Windows), the workspace and the Herdr panes inside the worktree are closed and the removal is retried once; a worktree Git had already unregistered but whose folder is left is finished by deleting that folder, and a folder still in use fails with a message saying so.

### `repair`

```text
shogun repair
```

Re-links the worktrees after the repository or the worktrees folder moved, as the desktop app does when you change a project's folders: the worktrees Git still lists at their old location are looked for under the project's worktrees folder, by their folder name, and re-linked (`repaired <path>`). With nothing to re-link it prints `Nothing to repair.` It needs the repository's project from the desktop app, since that's where the worktrees folder is stored; without it, it exits with `2`.

### `open`

```text
shogun open <task id | path | branch> --herdr [--agent <name>]
```

Opens one worktree in [Herdr](https://herdr.dev), the way the desktop app's "Open in Herdr" does: a focused workspace under the repository's space, a shell with the worktree's environment (what `shogun env` prints) and the project's agent launch command running in it (`opened <path> in Herdr with Claude (pane w1:p2)`). When Herdr already has the worktree open and one of its panes runs an agent (Herdr reports a status for it, `idle` and `done` included), that agent's pane is focused and nothing is typed (`focused <path> in Herdr (already open, pane w1:p3)`). When the workspace is open but no pane has a live agent, for example after quitting it, the agent is started in a new pane of that workspace with the worktree's environment, and the other panes are kept (`started Claude in <path>'s open Herdr workspace (pane w1:p4)`). Every line names the agent's pane, so an orchestrator knows where to write.

When the agent command runs Claude Code (`claude`, a path ending in `/claude`, or either after `headroom wrap` or environment assignments) as a single command without its own `-n`/`--name`, `open` adds `-n <name>` so the session can be told apart when messaging it: `<project>-<id>` for a worktree, where `<project>` is the repository's folder name and `<id>` the worktree's folder name (`main-repo-task-123`), and `<project>` for the main worktree. Names are lowercase letters, digits and single hyphens (other characters become a hyphen, at most 64 characters), and when a live Claude session already has the name, `-2`, `-3`… is appended. Another agent, or a command that chains several (`source env.sh && claude`), is typed unchanged. After the agent starts, `open` waits up to 10 more seconds for the session to appear under that name in Claude Code's session registry (`~/.claude/sessions`, or `$CLAUDE_CONFIG_DIR/sessions`) and appends it to the line (`opened <path> in Herdr with Claude (pane w1:p2) as session main-repo-task-123`). When it only focuses a live Claude agent, it reports the live session in that folder, preferring the name it would have given and otherwise the most recently started one. Without a confirmed name the line has no session and the open still succeeds. The registry is Claude Code's internal format, read only and tolerantly: if it changes or can't be read, names are simply not reported.

After typing the agent command, `open` waits up to 10 seconds for Herdr to detect the agent in that pane. If it doesn't, the command fails with exit code `1`, naming the pane and the command, and the pane is left open: an agent Herdr can't recognise, or a slow start, can be checked there.

The target is found as in `remove`, except that the main worktree is allowed. `--herdr` is the destination and is required; `--agent <name>` runs another of the project's agents, matched by name ignoring case, and an unknown name exits with `2` listing the project's agents. If Herdr isn't installed, its command fails or the agent doesn't start, the error is on standard error and the exit code is `1`.

### `clean`

```text
shogun clean [--yes|-y] [--delete-branches] [--stop-gradle-daemons] [--stop-emulators]
             [--delete-simulators] [--delete-derived-data] [--down-compose-projects [--compose-volumes]]
```

Selects the same worktrees as the desktop app's "Clean up": not the main one, not locked, not one where an agent is running, and either merged with no uncommitted changes or with their folder gone. Without `--yes` it only lists what it would do (`would remove …`) and ends with the command to run, repeating the options you gave and naming only what would be removed; with `--yes` it does it (`removed …`). With nothing to do it prints `Nothing to clean up.`

A worktree where an agent is running is listed as `kept <path> (claude is running)`; if running agents can't be checked on the platform, a note on standard error says so. A worktree whose status couldn't be read is listed as `skipped <path> (status unavailable: <reason>)`.

| Option | Also… |
|---|---|
| `--delete-branches` | Deletes the removed worktrees' branches if they're merged into the project's main branch (`kept branch …` otherwise). |
| `--stop-gradle-daemons` | Stops the idle Gradle daemons whose last build was in a removed worktree. |
| `--stop-emulators` | Stops the removed worktrees' Android emulators. |
| `--delete-simulators` | Deletes the removed worktrees' iOS simulators and orphaned ShogunAi simulators. |
| `--delete-derived-data` | Deletes orphaned Xcode DerivedData, the removed worktrees' folders included. |
| `--down-compose-projects` | Takes down the Docker Compose projects of the removed worktrees and removes the networks and built images they leave, keeping their volumes (`took down Compose project …`, `removed image …`, `kept volume …`; `would take down Compose project …` without `--yes`). Only for repositories that declare the `docker-compose` provider; a project that can't be taken down, or Docker being unavailable, is reported on standard error without changing the exit code. |
| `--compose-volumes` | With `--down-compose-projects`, also removes the volumes of the removed worktrees' projects. Without `--down-compose-projects` it is a usage error. |

The Herdr workspaces of removed worktrees are closed too (`would close Herdr workspace of …` in the listing). A daemon, emulator or Herdr workspace that can't be stopped or closed is reported on standard error without changing the exit code.

## Multirepos

In a multirepo folder (see [Multirepos and groups](multirepo.md)), `new`, `status`, `open` and `clean` act on its groups, and `shogun multirepo` configures it.

```text
shogun multirepo init [--worktrees-root <dir>] [--name <name>] [--use-worktrees-root | --keep-worktrees-folder] [--yes]
shogun multirepo show
shogun multirepo trust [<folder>] [--yes | --revoke]
shogun multirepo name <name> | --clear
shogun multirepo add-repo <alias> <path> [--use-worktrees-root | --keep-worktrees-folder]
shogun multirepo remove-repo <alias>
shogun multirepo defaults <alias>[,<alias>...] | --clear
shogun multirepo agent <command> [--name <name>] [--headroom]
shogun multirepo workspace set [--alias <alias>] [--default] | clear
shogun multirepo worktrees-root <dir>
shogun multirepo delete
shogun new <task-id> [--repos <alias>[,<alias>...]] [--fix] [--fetch]
shogun open <task id | branch> --herdr
shogun open --herdr
```

- `multirepo init` in a folder that already has a declaration (for example one that moved) shows the same review as `multirepo trust` and asks before approving it (`--yes` approves without asking; without a terminal, or with `--json`, and without `--yes` it exits with `1`; answering no changes nothing). Once approved it registers the multirepo and a project for each declared repository without one (asking about `<worktreesRoot>/<alias>` as `add-repo` does); `--worktrees-root` and `--name` exit with `2` there. Either way it first forgets the known multirepos whose folder no longer exists. Its `--json` for an existing declaration has `name`, `path`, `declaration`, `registered` (false when it was already approved and known) and the registered `projects`.
- `multirepo name` sets the name the multirepo is shown with; a blank name exits with `2`. `show` prints the name first, and its `--json` has `name`.
- `multirepo` subcommands work in the multirepo folder or anywhere in its repositories and worktrees, and accept `--json`; outside a multirepo they exit with `1`. A wrong alias exits with `2`.
- Only multirepos approved on this machine apply ([Approving a multirepo](multirepo.md#approving-a-multirepo)). Under a declaration that isn't approved, `new`, `status`, `open` and `clean` behave as outside a multirepo and print a note on standard error naming the file and `shogun multirepo trust`; hooks print nothing. `multirepo show` still shows it, with `not approved on this machine` (`"approved": false` in `--json`); the subcommands that write exit with `1`.
- `multirepo trust` shows the nearest declaration (or the one in `<folder>`): its declared name, each repository with its resolved path, the worktrees root and the workspace repository, marking paths outside the folder's parent. On a terminal it asks (`[y/N]`) and approves on yes; `--yes` approves without asking; without a terminal and without `--yes` it exits with `1`. `--revoke` forgets the multirepo and keeps the file. Its `--json` object has `path`, `declaration`, `name` (when declared), `approved`, `changed`, `repos` (with `outsideParent`), `worktreesRoot`, `worktreesRootOutsideParent`, `workspace` and `ignoredDeclaredAgent`; without `--yes` it only reports.
- `multirepo agent` keeps the multirepo agent on this machine, never in the declaration; an `agent` left in the declaration by an earlier version is ignored, and `show` says so (`ignoredDeclaredAgent` in `--json`). The agent runs its command as is; `--headroom` wraps it with `headroom wrap`. `--no-headroom`, from earlier versions where Headroom was on by default, is still accepted and changes nothing; combined with `--headroom` it exits with `2`.
- `new <id>` in the multirepo folder covers the default repositories, or `--repos` from anywhere in the multirepo; it prints the path of each worktree it creates on standard output and one line per repository on standard error, and exits with `1` if any repository failed. `--branch`, `--from` and `--worktrees-root` don't apply to a group and exit with `2`. Its `--json` object has the `branch` and a `repos` list with each `alias`, `path`, `result` (`created`, `existing` or `failed`), skipped post-creation commands and `error`.
- When the multirepo folder is a Git repository it is the multirepo's workspace repository (alias `workspace` unless declared otherwise): `show` lists it, marked `detected` until `workspace set` declares it (`--alias` renames it, `--default` adds it to the default repositories) and `workspace clear` turns detection off; in a folder that isn't a repository `workspace set` exits with `1`. `new <id> --repos workspace,app` includes it, with its worktree in `<worktreesRoot>/workspace/<id>`. `project add` and `app` run in that folder register the workspace repository as a member of the multirepo and no project of the folder's own (`app` just opens the app), unless detection is off. `--json` of `show` marks each repository with `workspace` and `detected`.
- `status` in the multirepo folder prints each group's branch followed by one line per repository, then `ungrouped`, with each Claude agent's session names as `status` shows them (`agents=claude(working, app-task-123)`); `--json` prints `groups` (each with `branch`, `title` and `repos` with `alias`, `path`, `branch`, `base`, `folderMissing`, `uncommittedChanges`, `ahead`, `behind`, `merged`, `agents`), `ungrouped` and `failures`.
- `open <id> --herdr` opens each worktree of the group with its project's agent (`--agent` exits with `2`), reporting each as `open` does for one worktree (opened, agent started in its open workspace, or focused, with its pane); a worktree that fails, or whose agent doesn't start, is reported on standard error, the others still open, and the exit code is `1`. An id matching both `feature/<id>` and `fix/<id>` exits with `2`, no match with `1`. `open --herdr` without a target opens the multirepo's agent the same way: it focuses its live agent, starts it in a new pane of its open workspace when it has none, or opens a new workspace, and checks that it started. Each Claude session gets a name as with `open` for one worktree: `<alias>-<id>` for a worktree of the group, where `<id>` is the group's branch without its prefix (`app-task-123`, `api-task-123`), and the multirepo's name for the multirepo agent, so a coordinating session can tell the group's sessions apart and message each by name.
- `clean` lists the candidate groups (`would remove the group …`) and the kept ones with their reason (`kept feature/TASK-2 (api isn't merged)`), and accepts every option of `clean`. After the groups it lists the loose worktrees of each repository (`would remove api <path>`) that are clean, merged and without a running agent, and removes them with `--yes`; with nothing to remove it prints `Nothing to clean up.`
- `hook agent-state` records the sessions of a multirepo folder that isn't a Git repository in a store under `~/.shogunai/agent-sessions/`; for any other directory that isn't a repository it still writes nothing.

## Projects and the app

### `project`

```text
shogun project add [<path>] [--json]
shogun project list [--json]
shogun project remove [<path>] [--json]
```

`add` registers the repository containing the path (the current directory by default, or its main checkout when run from a worktree) as a project of the desktop app, with the values the app's "Add project" form proposes: the folder's name as the project name, `<parent>/<name>-worktrees` as the worktrees folder, no local files and the main branch detected. Nothing inside the repository is written. A repository that is already registered is left exactly as it was (its settings aren't touched) and the command says so, exiting with `0`.

`list` prints one line per project (`name  repository path`) and works outside a repository. `remove` unregisters the repository's project and leaves the repository and its worktrees on disk; it finds a project whose folder no longer exists by the path it was registered with, and exits with `1` when the repository has no project.

A running app picks up the change when its window regains focus or on **View > Reload**.

### `app`

```text
shogun app [<path>] [--settings]
```

Opens the desktop app on the project of the repository containing the path (the current directory by default), registering the repository first if it has no project. With `--settings` it opens the project's settings instead of its worktrees, which needs a repository (exit code `2` without one). Outside a repository, without a path, it just opens the app or brings it to the front. With a path that isn't a repository it exits with `1` and asks for nothing.

The command returns as soon as the app has been asked, without waiting for it:

- **The app is open**: it serves the request in a second or so, through the same navigation as the sidebar. Its window, size and position are kept, and a form with unsaved changes asks whether to discard them instead of being replaced. If the project's settings are already open, nothing changes.
- **The app is closed**: `shogun` launches it and the app opens on the project once started. This works where the app's own launcher is the `shogun` command (macOS with Homebrew, Linux `.deb`, Windows `.msi`). From the standalone zip, with no app installed, the command says the desktop app isn't available from that install and exits with `1`.

## Environment

### `env`

```text
shogun env [<path>] [--format sh|dotenv|json]
```

Prints the environment of the worktree containing the path (or the current directory), from `.shogunai.json` `env` (once the repository is approved) and the providers, reserving its ports the first time. Declared variables awaiting approval aren't printed; a note on standard error names them and `shogun trust`. `sh` (the default) prints `export NAME='value'` lines for `eval "$(shogun env)"`; `dotenv` and `json` suit other tools. An empty environment prints nothing.

## Stacks

### `stacks`

```text
shogun stacks
```

Prints one line per stack detected in the repository (`Maven  evidence=pom.xml  maven=suggested`): the files that revealed it and, per provider it suggests, `suggested` or `declared` when `.shogunai.json` already has it. Providers the operating system doesn't support are not mentioned. After the stacks it prints one `Secret  <path>  kind=<kind>  link=<pattern>` line per mobile secret nothing provisions yet and one `Warning` line per keystore path that leaves the repository. It changes nothing, prints `No known stack detected.` and exits with `0` when there is nothing, and exits with `1` outside a Git repository. See [detecting stacks](providers.md#detecting-stacks).

## Providers

These commands act on the [providers](providers.md) a repository declares in `.shogunai.json`. Where a command needs a provider the repository doesn't declare, it says so and exits with `1`.

### `gradle`

```text
shogun gradle
shogun gradle stop [<pid>...]
```

`gradle` lists the Gradle and Kotlin daemons on the machine: pid, kind and version, `memory=`, `state=busy|idle|unknown` (with `idle-for=`) and `worktree=<path>|removed|other-project`, then the total (`No Gradle or Kotlin daemons running.` when there are none). It works outside a repository too. Inside a Gradle repository without Gradle's build cache, it ends with a notice.

`gradle stop` stops every idle daemon, or only the given process ids; busy and unknown daemons are kept (`kept <pid> … (<reason>)`). A pid that isn't a daemon exits with `1`.

### `maven`

```text
shogun maven
```

`maven` lists the Maven daemons (mvnd) on the machine: pid, `memory=` and, inside a Git repository, `worktree=<path>|removed|other-project|unknown`, then the total (`No Maven daemons running.` when there are none). It works outside a repository too and never stops anything.

### `compose`

```text
shogun compose
shogun compose up [<worktree>] [--build] [-- <args>...]
shogun compose build [<worktree>] [-- <args>...]
shogun compose down [<worktree>] [--volumes]
shogun compose prune [--volumes] [--yes]
```

`compose` lists the Docker Compose projects of the repository's worktrees: name, `status=` and `worktree=<path>|removed`, then one `leftover <kind> <name>  project=<project>` line per volume, network or image left by a removed worktree, with the `compose prune` command that removes them.

`compose up` starts the services of the given worktree, or of the one containing the current directory, with `docker compose up --detach` run in that worktree with its environment (its `COMPOSE_PROJECT_NAME` and port variables); `--build` adds `--build`, and the arguments after `--` are passed to Docker Compose as they are. `compose build` runs `docker compose build` the same way. Docker Compose's output is shown as it runs, then `started Compose project <name> for <path>` (or `built …`); a failure is printed on standard error with exit code `1`.

`compose prune` lists the networks and built images left by removed worktrees (`would remove …`) and the volumes it would keep (`would keep …`); with `--yes` it removes them (`removed …`, `kept …`), and their volumes too with `--volumes`. Nothing is forced, so a resource still used by a container is reported on standard error and the exit code is `1`. With nothing left it prints `Nothing to remove.` `compose down` takes down the projects of removed worktrees, or the project of the given worktree (found as in `remove`, the main one allowed), with `docker compose -p <name> down`: volumes are kept unless `--volumes` is given. It prints `took down <name> (volumes kept)`, reports a failure on standard error and exits with `1` for it. If the repository doesn't declare the `docker-compose` provider, or the given worktree doesn't exist, it exits with `1` and runs no Docker command.

### `emulator`

```text
shogun emulator start|stop|status [<path>] [--avd <name>] [--no-window] [--wait] [--wait-timeout <minutes>]
```

Starts (and waits for), stops or shows the Android emulator of the worktree. `--avd` and `--no-window` only apply to `start` and override the project's settings for that start. `status` prints `emulator-5556 running|booting|stopped`, or `not acquired` before the first start. Every `start` renews the worktree's lease, and prints `export ANDROID_SERIAL=…` with the serial to use. The first `start` reserves the console port (releasing an emulator whose lease expired if the pool is full, reported on standard error); run it before every use of the device, since it re-acquires one if it was released. After a release, `status` prints `not acquired (released for <worktree> at <time>)`. With every emulator protected it fails with exit code `1` naming the worktrees that hold them and when their leases expire.

With `--wait`, `start` waits in line instead of failing when the pool is full and none can be released, and starts the emulator as soon as it can acquire one. The wait lasts 30 minutes at most, or the minutes given with `--wait-timeout` (which implies `--wait`); then it fails with exit code `1`, saying how long it waited and who holds the emulators, having reserved nothing. Waiters of a pool are served first in, first out across every repository on the machine, and while anyone waits, a `start` without `--wait` (or from the desktop app) can't take the emulator they're waiting for: its error says how many are waiting. A waiter that is interrupted or whose process ends leaves the line. While it waits, the command prints on standard error its place in the line, who holds each emulator and when the next lease expires, again only when that changes, and `waited <time>` once it has the emulator. Agents should run a waiting `start` in the background, since it outlasts their shell's time limit. `--wait` and `--wait-timeout` only apply to `start`.

### `simulator`

```text
shogun simulator start|stop|status [<path>] [--device <type>] [--runtime <runtime>] [--wait] [--wait-timeout <minutes>]
shogun simulator gradle-init install|uninstall [--yes|-y]
```

Creates if needed, boots (and waits for) and opens, shuts down or shows the iOS simulator of the worktree (macOS only). `--device` and `--runtime` only apply when `start` creates the simulator. `--wait` and `--wait-timeout` wait for a full simulator pool as they do for [`emulator`](#emulator). In a Kotlin Multiplatform repository, `status` also says whether the KMP tests use the simulator.

`gradle-init install` installs a machine-wide Gradle init script that makes every project's KMP simulator tests use the worktree's simulator; it shows the script and asks `[y/N]` unless `--yes` is given. `uninstall` removes it.

### `headless`

```bash
shogun headless start [<path>] [--visible] [--fresh] [--json]
shogun headless capture [<path>] [--output <file>] [--json]
shogun headless stop|status [<path>] [--json]
```

Runs the worktree's Compose Desktop app in the background (macOS only, repositories with the [`desktop-app` provider](providers.md#compose-desktop-app)). `start` launches it through Compose Hot Reload without taking focus, waits for its window, hides it and prints `<pid> running hidden`; on an app already running it launches nothing and hides it again. `--visible` leaves the window shown, behind the app you're using. `--fresh` empties the app's isolated home first. A failed build exits with `1` and the end of the launch log; an app that exits before showing its window, with the end of its own log. If macOS denies hiding it, the app keeps running visible and standard error names the Accessibility permission.

`capture` saves a PNG of the app's window, taken from the window itself, so it shows the app even while hidden or covered; without `--output` it goes to a new file in the worktree's Git folder, and the command prints its path. It needs the Screen Recording permission for the application that runs `shogun`, such as your terminal. `stop` ends the app (`stopped <pid>`, or `stopped` when it wasn't running); `status` prints its state, the isolated home, the launch log and any missing permission. See [Checking a desktop app in the background](headless-app.md).

### `desktop`

```bash
shogun desktop acquire [<path>] [--wait] [--wait-timeout <minutes>] [--json]
shogun desktop release [<path>] [--json]
shogun desktop status [--json]
```

The desktop (screen, mouse, keyboard and the focused app) is one resource for the whole machine, so agents that check something with computer use take turns: one worktree holds the desktop turn at a time, on any platform. `acquire` gives the turn to the worktree containing the path or the current directory and prints `acquired the desktop until 14:10`; run by the holder again, it renews the turn (`renewed the desktop until 14:15`). The turn expires on its own after the desktop turn duration (10 minutes by default, in Preferences › Resources) unless it's renewed, so an agent that crashes doesn't keep it; the agent working doesn't renew it. Claude Code's `Stop` and `SessionEnd` [hooks](../agents.md#claude-code) release it when the holder's response or session ends, and removing or cleaning up a worktree releases its turn and drops its waits.

While another worktree holds the turn, `acquire` fails with exit code `1`, naming the holder, when its turn expires and how many worktrees are waiting. With `--wait` it waits in line instead, for 30 minutes at most or the minutes given with `--wait-timeout` (which implies `--wait`), printing on standard error its place in the line and who holds the desktop until when, only when that changes, and `waited <time>` once it has the turn. Waiters are served first in, first out across every repository, and an `acquire` without `--wait` can't take the turn while anyone waits. Agents run a waiting `acquire` in the background.

`release` gives the turn back (`released the desktop`), says `nobody holds the desktop` when nobody does, and fails with exit code `1`, changing nothing, when another worktree holds it; the desktop app can release anyone's turn. `status` prints the holder with since when and until when, or `desktop free`, followed by the waiters in order. The turn is cooperative: it keeps agents that follow the [workflow rules](../agents.md#shogunais-workflow-rules) from colliding, it doesn't stop a program that ignores it.

### `pool`

```text
shogun pool status [--json]
shogun pool trim [--json]
```

`status` prints each pool (`emulators 1/2`, `simulators 0/1`, `gradle daemons 3/2  over the limit`) with the limit of the machine's capacity profile and, under it, the project, worktree and state of every resource in use, followed by the worktrees waiting for an emulator or simulator (`waiting: main-repo · TASK-2 since 14:10`), in queue order. `trim` stops idle Gradle daemons, longest idle first, until they are within the limit, and prints what it stopped and kept.

### `xcode`

```text
shogun xcode
shogun xcode clean
shogun xcode trim [--dry-run]
```

`xcode` lists the DerivedData folders of the repository's worktrees with their size, the orphaned folders on the machine (`orphans: N (size)`) and, when compilation caching is declared, the cache size. `xcode clean` deletes the orphaned folders (`deleted <path>`, `freed <size>`, or `Nothing to delete.`). `xcode trim` applies the DerivedData quota set in Preferences > Resources, automatic by default (a quarter of the free disk space plus what DerivedData takes, between 20 and 200 GB): when the folders add up to more than the quota it releases orphaned folders first and then the least recently accessed folders of worktrees of your saved projects that are not in use, and stops as soon as the total fits (`released <path>  <size>`, `freed <size>`, `usage <size> of <quota>`, followed by `(automatic)` for the automatic quota). A worktree is in use while its agent is working or blocked or its folder was accessed within the last hour, and folders of other projects, on unavailable volumes and the compilation cache are never released; if only those remain it prints `quota still exceeded` and the worktrees in use. `--dry-run` prints `would release` and `would free` without deleting. With "No quota" it says so and deletes nothing, and when the automatic quota can't be computed because the free disk space is unknown it says that instead. The `xcode` commands remember each folder's size between runs, shared with the app, and only measure again the folders Xcode has used since, several at a time. Without the Xcode provider, or off macOS, `xcode` says the provider isn't active and exits with `0`.

## JSON output

Besides `status --json` above, every command that reports a result accepts `--json`: `new`, `base`, `provision`, `trust`, `remove`, `repair`, `open`, `clean`, `gradle`, `gradle stop`, `stacks`, `maven`, `compose`, `compose up`, `compose build`, `compose down`, `compose prune`, `pool status`, `pool trim`, `project add`, `project list`, `project remove`, `xcode`, `xcode clean`, `xcode trim`, `emulator`, `simulator`, `hooks` and `guide install|uninstall|status` (not `simulator gradle-init`, `env`, `hook`, `help` or `--version`; on those it is an unknown option and exits with `2`). The option can go anywhere after the command.

The rules are the same for all of them:

- One compact JSON object on a single line on standard output, instead of the text, with the same exit code as the text output.
- An error before the result exists is printed as text on standard error, with nothing on standard output.
- It never asks a question. Post-creation commands and declared variables that aren't approved on this machine are skipped and listed (`shogun trust` approves them), and `trust` only reports unless `--yes` is given.
- Every documented field is always present, `null` when it doesn't apply, lists empty rather than missing, and enumerations in lowercase. Paths are absolute. Later versions only add fields, never rename, remove or change the type of one.

| Command | Object |
|---|---|
| `new` | `path`, `branch`, `created` (always `true`), `skippedPostCreateCommands` (a list of argv lists), `skippedVariables` (the names of the declared variables left out), `postCreateFailure`: `null`, or `{ command, exitCode, stderr }` when a post-creation command failed, and `skippedLinks`, the symbolic links skipped inside local folders. In that case the object is still printed, since the worktree exists, and the exit code is `1`. |
| `provision` | `path`, `copied`, `linked` and `copiedInsteadOfLinking` (file paths), `skippedPostCreateCommands`, `skippedVariables` and `postCreateFailure`, as in `new`, `removed` (files deleted because their local folder no longer has them) and `skippedLinks`. When a command failed, the three file lists are empty. |
| `provision --check` | `path` and `drift`: a list of `{ path, state }` with `state` `missing`, `differs`, `not-linked` or `deleted-in-base`. The exit code is `3` when `drift` isn't empty. |
| `trust` | `repository`, `commands` (argv lists), `env` (a list of `{ name, template }`), `envFile` (or `null`), `approved` and `changed` (this run approved or revoked it). A repository that declares nothing is `approved: true` with empty lists. `--revoke` reports `approved: false` when there was something to approve. |
| `remove` | `path`, `branch` (the worktree's, or `null` if detached), `branchDeleted` (the name, or `null` when it was kept or no branch deletion was requested), `branchKeptReason` (why a requested deletion didn't happen, or `null`), `providers` (`emulatorsStopped` serials, `simulatorsStopped` udids and `gradleDaemonsStopped` pids) and `failures` (messages of what couldn't be stopped, exit code `1`). |
| `hooks install`, `hooks uninstall` | `path`, `applied` (written this run) and the `added` and `removed` hooks as `{ event, command }` plus `replaced` as `{ event, from, to }`. |
| `hooks status` | `user` and `project` (`null` outside a repository): `path`, `state` (`installed`, `outdated`, `missing`) and `events`. |
| `guide install`, `guide uninstall` | `path`, `applied` (written this run), `state` (`installed`, `outdated`, `missing`) and `warnings` (messages). |
| `guide status` | `targets`: `{ target, path, state, duplicates }` with `target` `codex`, `gemini`, `project` or `file` and `duplicates` `true` when the file mentions `shogun new`, `open` or `clean` outside the block; and `warnings`. |
| `repair` | `repaired`: the re-linked worktree paths. |
| `project add`, `project remove` | `action` (`added`, `alreadyRegistered` or `removed`), and the project's `id`, `name` and `repository`. |
| `project list` | `projects`: `{ id, name, repository }`, empty without projects. |
| `open` | `path`, `branch` (or `null` if detached), `herdr` (`opened`, `started-agent` or `focused`), `pane_id` (the agent's pane), `agent` (the started agent's name, or the one Herdr reports in the focused pane) and `session_name` (the Claude Code session's confirmed name, or `null`). In the multirepo folder with a target: `branch` and `repos`: `{ alias, path, result, pane_id, agent, session_name, error }` with `result` `opened`, `started-agent`, `focused` or `failed`. Without a target: `path`, `opened` (`false` only when focused), `herdr`, `pane_id`, `agent` and `session_name`. |
| `clean` | `dryRun`; `candidates`: `{ path, branch, reason }` with `reason` `merged` or `folder-missing`; `keptForAgents`: `{ path, agents }`; `skipped`: `{ path, reason }` (status unavailable); `removed`; `deletedBranches`; `failures`: `{ path, message }`; and `providers`: `gradleDaemonsStopped`, `emulatorsStopped`, `simulatorsDeleted`, `derivedDataDeleted` and, with `--down-compose-projects`, `composeProjectsDown` and `composeResources` (`removed`, `kept`, `failures`, as in `compose prune`). Without `--yes` (`dryRun: true`) `removed`, `deletedBranches` and the `providers` lists are empty. |
| `gradle` | `daemons`: `{ pid, kind, version, memoryBytes, state, idleSince, worktree }` with `kind` `gradle` or `kotlin`, `state` `busy`, `idle`, `unknown` or `untracked`, `idleSince` in ISO-8601 UTC (only when idle) and `worktree` a path, `removed`, `other-project` or `null` outside a repository; and `totalMemoryBytes`. |
| `gradle stop` | `stopped` (pids), `kept`: `{ pid, kind, reason }` with `reason` `busy`, `state-unknown` or `build-running`, `notDaemons` (pids) and `failures`: `{ pid, message }`. The exit code is `1` when either of the last two isn't empty. |
| `stacks` | `stacks`: `{ stack, evidence, providers }` with `stack` `gradle`, `android`, `xcode`, `maven` or `docker-compose`, `evidence` the files that revealed it and `providers`: `{ id, declared }` for each provider it suggests; `secrets`: `{ path, kind, pattern }` with `kind` `local-properties`, `signing-properties`, `keystore` or `firebase-config`; and `keystoreWarnings`: `{ file, key, value }`. |
| `maven` | `daemons`: `{ pid, memoryBytes, worktree }` with `worktree` a path, `removed`, `other-project` or `null` (unknown, or outside a repository); and `totalMemoryBytes`. |
| `compose` | `projects`: `{ name, status, worktree }` with `worktree` a path or `removed`; `leftovers`: `{ kind, name, project }` with `kind` `network`, `image` or `volume`. |
| `compose up`, `compose build` | `worktree`, `project`, `succeeded` and, on failure, `message`. Docker Compose's output goes to standard error. |
| `compose prune` | `dryRun`; `removed` and `kept`: `{ kind, name, project }`; `failures`: `{ resource, message }`. Without `--yes` `removed` is what it would remove. |
| `compose down` | `takenDown` (project names) and `failures`: `{ name, message }`. The exit code is `1` when `failures` isn't empty. |
| `xcode` | `active` (`false` when the provider isn't active, with the rest empty), `folders`: `{ path, sizeBytes, worktree }`, `orphans`: `{ path, sizeBytes }`, `unavailableVolumeFolders` (a count) and `compilationCacheBytes` (`null` unless declared). |
| `xcode clean` | `active`, `deleted` (paths), `freedBytes` and `failures`: `{ path, message }`. |
| `xcode trim` | `active`, `quotaMode` (`automatic`, `fixed` or `none`), `quotaBytes` (`null` without a quota or when the automatic one can't be computed), `dryRun`, `usageBeforeBytes`, `usageAfterBytes`, `exceeded`, `released` (paths, those that would be released with `--dry-run`), `freedBytes`, `inUse` (worktree paths) and `failures`: `{ path, message }`. |
| `emulator start\|stop\|status` | `worktree`, `serial` and `state` (`running`, `booting` or `stopped`). `serial` and `state` are `null` when the worktree has no emulator port reserved yet. `waited` is the whole seconds a `start --wait` waited for capacity (`0` if there was room), `null` otherwise. |
| `desktop acquire\|release\|status` | `holder` (`null`, or `{ repository, worktree, since, expiresAt }`) and `waiting`: `{ repository, worktree, since }` in line order, with ISO-8601 UTC times. `acquire` adds `renewed` and `waited` (whole seconds waited, `0` when the desktop was free, `null` without `--wait`); `release` adds `released`. |
| `simulator start\|stop\|status` | `worktree`, `udid`, `name` and `state` (`shutdown`, `booting` or `booted`), `null` when the worktree has no simulator yet, and `waited` as for `emulator`. The KMP wiring hint isn't part of it. |

```bash
shogun clean --json | jq -r '.candidates[].path'
shogun emulator status --json | jq -r '.serial'
shogun provision --check --json | jq -e '.drift | length == 0'
```

## Agent hooks

These commands are meant to be called by agents and tools, not typed. How to configure them is in [Using with agents](../agents.md).

| Command | Called by |
|---|---|
| `shogun hook worktree-create` | Claude Code's `WorktreeCreate` hook. Reads `name` and `cwd` from standard input, creates (or reuses) a provisioned worktree on `feature/<name>` starting at the HEAD of the worktree in `cwd`, and prints its path. If a post-creation command fails it still prints the path as the last line and exits with 0, with the failure on standard error, so Claude Code uses the worktree. Unapproved post-creation commands and declared variables are skipped with a note on standard error; it never asks. |
| `shogun hook worktree-remove` | Claude Code's `WorktreeRemove` hook. Removes the worktree in `worktree_path`, deletes its branch only if it's merged into the project's main branch (a branch merged only into its own upstream is kept, with a note on standard error) and closes its Herdr workspace. |
| `shogun hook session-start` | Claude Code's `SessionStart` hook. Inside a Git repository, prints the context. Only when the repository is registered as a project does it append the worktree's environment to `CLAUDE_ENV_FILE` (nothing when the repository declares no `env`) and print a line naming the variables, without their values; declared variables awaiting approval are named on a line of their own, telling the agent to ask you to review them with `shogun trust`, and aren't delivered. Then come the short [workflow rules](#guide) that apply to that location, at most ten lines, starting with the precedence line and ending with a pointer to `shogun guide`. Without Herdr it leaves out the rules about `shogun open --herdr` and messaging sessions; the emulator and simulator rules appear only when the repository declares those providers. Outside a Git repository it prints nothing and exits with `0`. |
| `shogun hook agent-state` | Claude Code's `UserPromptSubmit`, `Notification`, `Stop` and `SessionEnd` hooks. Reads the hook's JSON from standard input and records the session's state (working, blocked, done) for the worktree containing `cwd`, or removes the record on `SessionEnd`; a `Notification` of type `idle_prompt` means done and `auth_success` is ignored. When the state is working it also renews the leases of the emulator and simulator the worktree holds. It prints nothing, never blocks the session and exits with 0 also outside a repository; input that isn't JSON exits with 2. See [Agent states outside Herdr](../agents.md#agent-states-outside-herdr). |
| `shogun hook herdr` | The ShogunAi plugin for Herdr, on `worktree.created` (provisions the worktree, skipping unapproved post-creation commands with a note) and `worktree.removed` (deletes its branch if merged into the project's main branch). Reads `HERDR_PLUGIN_EVENT`, `HERDR_PLUGIN_EVENT_JSON` and `HERDR_BIN_PATH`. |

## Help and version

| Command | Prints |
|---|---|
| `shogun help`, `-h`, `--help` | The usage summary. |
| `shogun <command> --help`, `-h` | The usage and options of that command, with exit code 0. |
| `shogun version`, `-v`, `--version` | `shogun <version>`, the same version as the app. |
