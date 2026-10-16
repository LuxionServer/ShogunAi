# Design

## Context

`core` holds every use case and port implementation without UI; `desktopApp` wires them in `AppContainer` and opens a Compose window from `main()`. Packaged builds (`.dmg`, `.deb`, `.msi`) are jpackage images with a jlink runtime that has **no `bin/java`** (jpackage strips native commands), so a script can't reuse the bundled JVM; only the app's native launcher can start it. CI builds with JDK 21. Releases use the `packageRelease*` tasks (ProGuard with Compose's default rules).

Measured before planning, on an Apple Silicon Mac with a prototype `status` command that lists worktrees through `core`:

| How it's started | Wall time |
|---|---|
| `java -cp …` with a JDK 27 (Gradle `installDist`) | ~0.08 s warm, ~0.55 s first run |
| The packaged app's launcher (`ShogunAi.app/Contents/MacOS/ShogunAi status`) | ~0.10 s |
| Same, through a symlink in another folder (what Homebrew's `binary` creates) | works, keeps the current directory and the exit code |

So a JVM is fast enough for a hook, and the app's launcher is already a working, self-contained `shogun` on macOS and Linux.

## Goals / Non-Goals

**Goals:**

- A `shogun` command with `new`, `provision`, `status` and `clean` that behaves exactly like the app, because it calls the same use cases.
- Installable without a separate runtime where the app is installed, and usable anywhere with Java.

**Non-Goals:**

- Claude Code's `WorktreeCreate` hook contract (stdin JSON, printing the path) and recipes for other agents: next step.
- Interactive prompts, colors or a TUI; machine-readable output formats (`--json`) until a consumer needs them.
- Managing the project catalog from the CLI (`shogun init`); projects are created in the app, and the CLI works without one.
- Native images (GraalVM): the measured JVM startup doesn't justify the extra build per platform.

## Decisions

### One launcher for both front ends

`desktopApp`'s `main(args)` runs the CLI and exits when it receives arguments, and opens the window otherwise. On macOS and Linux the packaged launcher forwards arguments, standard streams and the exit code, so the installed app *is* the command:

- macOS: the Cask adds `binary "#{appdir}/ShogunAi.app/Contents/MacOS/ShogunAi", target: "shogun"`.
- Linux: `/opt/shogunai/bin/ShogunAi` is the command; the README shows `sudo ln -s /opt/shogunai/bin/ShogunAi /usr/local/bin/shogun`. Compose's `.deb` has no hook to create that link, and adding a custom package script isn't worth it yet.
- Arguments starting with `-psn_` (old macOS process serial numbers passed by Finder) are ignored, so a Finder launch never turns into a CLI run.

The CLI path never touches AWT or Compose, so no window or Dock icon appears.

Alternatives: jpackage's `--add-launcher` (a second native launcher) isn't exposed by the Compose Gradle plugin; GraalVM native images need a build per OS and reflection configuration for `kotlinx.serialization`; a script calling a system `java` would require users to install Java.

### Windows and everything else: a standalone zip

jpackage's Windows launcher is a GUI-subsystem executable: it has no console, so it can't print. Instead of switching the app to a console launcher (which would open a console window behind the app), each release also attaches `shogun-<version>.zip`, built once with Gradle's `application` plugin (`:cli:distZip`): `bin/shogun`, `bin/shogun.bat` and the jars, requiring Java 21+ on `PATH`. In that distribution, running `shogun` with no arguments prints the help.

### `cli` module

`cli` depends on `core` and applies the `application` plugin (main class `app.luxion.shogunai.cli.MainKt`, application name `shogun`). `desktopApp` depends on `cli`. The entry point is a `ShogunCli` class built from the ports (executor, file manager, project repository, provisioning source), standard output and error writers and the working directory, with `run(args): Int`; `main` builds it with the real implementations. Tests drive `ShogunCli` with the `core` test fixtures and string writers, so every command is tested without processes or disk.

Argument parsing is hand-written (four commands, a handful of flags): a parse step turns `args` into a sealed `CliCommand` or a usage error, tested on its own. A library such as Clikt would add a dependency and startup time for little gain at this size.

### Finding the repository and the project

A new `ResolveRepositoryUseCase` in `core` runs `git rev-parse --path-format=absolute --git-common-dir` from the given directory: the main worktree is the parent of the common `.git` folder, which works from the main worktree, from linked worktrees and from subfolders. Failure (not a repository, bare repository) is a new `WorktreeError.RepositoryNotFound(path)`.

The project is the one in `~/.shogunai/projects.json` whose base repository path is the same directory, compared by canonical path (`/tmp` vs `/private/tmp` on macOS). Without a project the CLI builds a `ProjectConfig` with no local files, no main branch (detected) and the worktrees folder from `--worktrees-root`, which only `new` needs.

### Provisioning an existing worktree

A new `ProvisionWorktreeUseCase(config, executor, fileManager, provisioningSource)(worktreePath)` lists the worktrees (`ListWorktreesUseCase`), rejects the main one and unknown paths, runs the same pre-check and resolution as creation (`checkProvisioningAndResolveFiles`), and copies local files and resolved files with the copy function shared with creation, returning the copied relative paths. A failed copy throws `SecretCopyFailed` without removing anything: the worktree isn't ShogunAi's to delete.

### Output

Plain English text, one item per line, nothing decorative, so it's easy to read and to `grep`: `new` prints only the path; `provision` the copied paths; `status` one line per worktree (`path`, branch, then `key=value`-style details such as `origin=claude-code changes=3 ahead=1 behind=0 merged`); `clean` the candidates and, with `--yes`, `removed`/`kept branch`/`failed` lines. Errors go to standard error, using `WorktreeError` messages. Exit codes: 0 success, 1 operation failed, 2 usage error.

### Version

`BuildInfo` (generated from `-PappVersion`) moves from `desktopApp` to `core`, so the app and `shogun --version` report the same version, and the zip is named after it.

## Risks / Trade-offs

- [ProGuard in release builds strips or renames something the CLI path needs] → The CLI is reached from `main`, so it's kept; verified by running the CLI from a `packageReleaseDmg` build, not only `createDistributable`.
- [A future macOS or jpackage version stops forwarding arguments or streams] → Covered by running the packaged launcher in the integration check of every release-related change; the zip remains as a fallback.
- [Users on Windows need Java 21] → Documented; a console-capable native build can come later if there's demand.
- [Output format changes break scripts] → Only `new`'s single-line path is meant for scripts at this point (the hook needs it); the rest is for people until `--json` exists.

## Migration Plan

Nothing to migrate. Existing installs get the command with the next release: `brew upgrade --cask shogunai` adds the `shogun` link.
