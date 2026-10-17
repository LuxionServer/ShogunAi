# Design

## Context

See proposal.md for the motivation. Facts that shape the approach:

- Claude Code's `SessionStart` hook (also `Setup`, `CwdChanged`, `FileChanged`) receives `CLAUDE_ENV_FILE`; every Bash command of the session runs that file's contents first, so `export` lines appended to it apply to the whole session. The hook's plain stdout is added to Claude's context. A `WorktreeCreate` command hook can only print the path, so the environment can't travel through it.
- Git keeps one administrative directory per linked worktree (`<common-dir>/worktrees/<id>/`, from `git -C <worktree> rev-parse --absolute-git-dir`) and deletes it on `git worktree remove` and `git worktree prune`. Git ignores files it doesn't know there. The main worktree's administrative directory is the common directory itself.
- `.shogunai.json` is parsed as a generic `JsonObject` in `RepositoryProvisioningFiles`, unknown fields are kept when writing, and invalid content throws `InvalidRepositoryConfig` before creation runs any Git command.
- Creation (`CreateWorktreeUseCase`, `CreateWorktreeFromBranchUseCase`) and provisioning (`ProvisionWorktreeUseCase`) share the copy step and its rollback; the CLI resolves the worktree containing a directory with `ListWorktreesUseCase` and `owningWorktree`.

## Goals / Non-Goals

**Goals:**
- One resolution path for every consumer: creation, provisioning, `shogun env`, the `SessionStart` hook and the environment file.
- Reservations that need no cleanup of their own and work for worktrees created by any tool.
- Nothing written into the working tree unless the repository asks for it (`envFile`).

**Non-Goals:**
- Per-worktree Gradle settings: `org.gradle.jvmargs` belongs to the daemon and `GRADLE_OPTS` only configures the client JVM; mismatches start single-use daemons. It needs its own spike.
- The emulator provider (`ANDROID_SERIAL`); whether the Android Gradle plugin honours it is still to be verified there.
- Port uniqueness across different repositories beyond "free on the machine when reserved".
- Delivery through `CwdChanged` (Claude moving between worktrees in one session) and container or process isolation.

## Decisions

### Declaration parsed and validated up front
`RepositoryProvisioning` gains `environment: EnvironmentDeclaration` with `variables: List<EnvironmentVariable>` (in file order) and `envFile: String?`. Each value is parsed into template parts (`Literal`, `WorktreeName`, `Port(key)`) when the file is read, so an unknown placeholder, a bad name or an invalid `envFile` fails as `InvalidRepositoryConfig` before any Git command, like the other fields. `${port}` uses the variable's name as key; `${port:<key>}` uses `port:<key>`, which can't collide with a variable name because `:` isn't allowed in one.
Alternative: resolving raw strings lazily would report errors only when a worktree is used, after creation started.

### Reservations in Git's administrative directory
A new port `EnvironmentStateStore` reads and writes `shogunai-env.json` (`{ "ports": { "PORT": 20001, "port:api": 20002 } }`) in a worktree's administrative directory, lists the reservations of every worktree of the repository (the common directory plus each `worktrees/*/`), and runs a block under an exclusive lock on `<common-dir>/shogunai-env.lock` (`FileChannel.lock`), so parallel creations (several Claude sessions) can't reserve the same port. `NioEnvironmentStateStore` writes atomically like `RepositoryProvisioningFiles`. The object leaves room for later providers (an emulator serial).
Alternatives: `~/.shogunai/` (machine state that needs its own cleanup and breaks when repositories move); a file in the working tree (shows up in `git status`, needs ignoring).

### Free-port check through a port
`PortProbe.isFree(port)` binds a `ServerSocket` on the wildcard address with `reuseAddress = false` and closes it. It's only consulted when reserving; a reserved port is never re-checked, because the worktree's own server is expected to be listening on it.

### One use case resolves, others consume
`ResolveWorktreeEnvironmentUseCase(worktreePath, reserve: Boolean)`:
1. reads the declaration; empty → empty result, no state written;
2. finds the administrative directories with `git rev-parse --absolute-git-dir` / `--git-common-dir` in the worktree;
3. under the lock: loads all reservations, keeps this worktree's ones still in use, reserves missing keys from 20000–29999 skipping every key reserved by any worktree and every port `PortProbe` reports busy, releases unused keys, writes the state if it changed;
4. renders the templates and returns `WorktreeEnvironment(variables: List<Pair<String, String>>, ports: Map<String, Int>)`.
With `reserve = false` (the desktop row) it only reads the stored ports and never locks or writes.

`EnvironmentFormat` renders `sh` (`export NAME='…'`, single quotes with `'\''` escaping), `dotenv` (`NAME=value`, double-quoted with `\\`, `\"`, `\n` escapes when the value isn't plain) and `json`.

### Environment file next to the copy step
`WriteEnvironmentFileUseCase` resolves `envFile` inside the worktree (normalized, must stay inside), refuses a tracked path (`git ls-files --error-unmatch -- <path>` succeeds, `EnvironmentFileTracked`), and writes the dotenv text with a new `FileManager.writeText(path, text)` (atomic, creating parent folders); a write error is `EnvironmentFileWriteFailed`, not `SecretCopyFailed`, so the message names the right thing. `PrepareWorktreeEnvironmentUseCase` chains resolve-with-reserve and this, and creation and provisioning receive it as a `fun interface WorktreeEnvironmentPreparer` after copying files; creation's rollback covers it. The parameter defaults to `WorktreeEnvironmentPreparer.NONE` so the ~35 existing creation tests stay unchanged; the six production call sites (app container and CLI) pass the real one.

### CLI
`CliCommand.Env(path, format)` and `CliCommand.HookSessionStart`. `env` resolves with reservation (lazy reservation for worktrees made by other tools). `hook session-start` reads `cwd` from the hook JSON, appends `sh` lines to `CLAUDE_ENV_FILE` when set (read through an injected environment lookup so tests don't touch the real environment), and prints `ShogunAi environment of worktree <name>: PORT=20001, …`. Both exit 0 with an empty environment, and the hook also exits 0 when `cwd` isn't inside a Git repository, so sessions outside repositories are never disturbed. `owningWorktree` became public so the CLI picks the worktree containing a directory with the same nested-worktree rule as the app.

### Desktop row
`WorktreeViewModel` loads `ports` per worktree with `reserve = false` in the overview load, next to sizes, and `WorktreeOverviewLine` shows them (`PORT 20001 · api 20002`).

## Risks / Trade-offs

- [Two repositories reserve the same port while neither is listening] → Both are checked free at reservation; the range is wide. Documented; a machine-wide registry can come later if it matters.
- [Writing into `.git/`] → Only into ShogunAi's own file in directories Git ignores files in; it goes away with the worktree. A missing administrative directory (prunable worktree) means no reservation and an error for that worktree only.
- [A program other than the worktree's server holds its reserved port] → Not detected after reservation; the variable stays stable, which is what agents and scripts rely on.
- [`CLAUDE_ENV_FILE` lines also reach PowerShell only if they're plain assignments] → The hook writes plain `export NAME='value'` lines.
- [Lock file on network filesystems] → `FileChannel.lock` may be advisory or unsupported; failures surface as errors, not silent duplicates.

## Migration Plan

No migration: repositories without `env` behave as before and older versions ignore the new fields. Existing worktrees get reservations the first time their environment is asked for.
