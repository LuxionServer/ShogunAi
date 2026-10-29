# Proposal

## Why

When ShogunAi doesn't work on someone else's machine, nothing says why: a demo on another Mac showed `shogun` reported as missing from the `PATH`, Claude Code's hooks read as unusable and worktree creation failing on a local file, and each had to be worked out by hand. The host environment is now resolved in one place (the `host-environment` change, PR #237) and worktree creation checks local files before acting (`worktree-preflight`, PR #238); this change uses both to check a whole installation at once and say how to fix each problem. It is the second bullet of roadmap item 1, "Reliability on any machine".

## What Changes

- A new installation check, run by `shogun doctor` in the CLI and by "Check Installation…" in the desktop app's Help menu, that changes nothing and reports one result per check, each with its state (passed, warning, failed, or not found for optional tools) and, when it didn't pass, the fix to apply:
  - Git: found, and its version is at least the one ShogunAi needs (2.29, for `git worktree repair`).
  - The `PATH`: where it came from (the login shell, the process, or a fallback because the login shell couldn't be read).
  - The `shogun` command: found on that `PATH`, installed but not on it, or a launcher whose target no longer exists.
  - Claude Code's hooks in the user's settings: installed, outdated or missing, and whether the `shogun` they call resolves to a launcher that exists.
  - Claude Code, Herdr and Orca: found or not, with where.
  - Each registered project: its base folder exists and every local file it declares (the project's own and those of `.shogunai.json` and `.worktreeinclude`) is available, with the same per-file reasons and fixes as worktree creation.
- `shogun doctor` prints one line per check and the fix under it, `--json` prints the same as JSON, and the command exits with a failure code when a check failed (warnings and missing optional tools don't fail it).
- The desktop app shows the results in a window in English and Spanish, can run the check again, and its diagnostic report gains an "Installation" section with the same results in English, redacted like the rest of the report.
- Out of scope: fixing problems automatically, checking multirepos and their groups, other agents than Claude Code, and checks of tools that only some providers need (Xcode, the Android SDK, Docker).

## Capabilities

### New Capabilities

- `installation-check`: checking, without changing anything, that Git, the `PATH`, the `shogun` command, Claude Code's hooks, the agent tools ShogunAi integrates with and each project's local files are usable on this machine, with a fix for every problem, from the CLI and the desktop app.

### Modified Capabilities

- `local-diagnostics`: the diagnostic report includes the installation check's results.

## Impact

- Depends on PR #237 (`host-environment`): the check reads the `PATH` and its source, the home folder and `CLAUDE_CONFIG_DIR` from `HostEnvironment`, and finds tools with `ExecutableLocator(environment)`. This branch builds on PR #237's branch until it is merged into `develop`.
- Core: a new use case and its result model; the local files' availability check of `WorktreeCreation.kt` exposed as a function that returns the unavailable files instead of throwing; an English renderer shared by the CLI and the diagnostic report.
- CLI: `shogun doctor [--json]`, its help and its JSON.
- Desktop app: a Help menu item, a check window, English and Spanish texts, and the diagnostic report built with the check's results.
- Docs: the CLI and desktop app guides (English and Spanish), and the roadmap when the change is archived.
