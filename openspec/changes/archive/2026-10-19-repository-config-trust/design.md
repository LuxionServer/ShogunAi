# Design

## Context

Findings come from a project review on 2026-10-19 (see proposal.md). Today `WorktreeCreation.runPostCreateCommands` runs `ProvisionedFiles.postCreateCommands` unconditionally after `applyProvisionedFiles` and `prepareEnvironmentOrRollback`, and the same path is reached from the desktop create dialog, `shogun new`, `shogun provision`, `shogun hook worktree-create` and `shogun hook herdr`. `ShogunCli.configFor` builds a default `ProjectConfig` for any Git repository, so the hooks reach repositories that were never added to ShogunAi. `RepositoryConfigRules.isInsideWorktree` checks the path text only; `NioFileManager.copy`, `createSymbolicLink` and `writeText` follow symlinked parents. `dotenvValue` escapes `\`, `"`, `$` and newlines but not backticks. `CopyWorktreeLaunchCommandUseCase` writes `cd "<path>"` on every platform. Commands run as argv everywhere; there is no `sh -c` in ShogunAi itself.

## Goals / Non-Goals

**Goals:**
- No repository-declared command runs on this machine before the user has seen that exact list.
- No repository-declared path or variable makes ShogunAi write, delete or execute outside the worktree.

**Non-Goals:**
- Sandboxing approved commands: once approved they run with the user's rights, as `npm install` would.
- Trust for `copy` and `link` patterns: they only read from the base repository and, with the real-path check, only write inside the worktree.
- Signing or sharing approvals between machines or teammates.

## Decisions

**Approval key and content.** Key: the repository's main worktree real path (`toRealPath`), so a moved repository asks again, which is acceptable. Value: SHA-256 of the canonical JSON of the `postCreate` list (argv arrays, in order) plus the list itself for display. Stored in `~/.shogunai/trust.json`, written atomically with the existing temp-file-and-move pattern (0600). Alternative: hash the whole `.shogunai.json`; rejected, any unrelated edit (a port range) would ask again and train the user to click through.

**A port for the decision.** A new domain port `PostCreateApproval` with `suspend fun decide(repository: String, commands: List<List<String>>): PostCreateDecision` (`Run`, `Skip`) is passed to the creation and provisioning use cases. The use case checks the trust store first and calls the port only when not approved; `Run` records the approval before running. The result type gains `skippedPostCreateCommands: List<List<String>>` so every entry point can report it. Alternative: check trust in each entry point before calling the use case; rejected, five entry points would each have to remember it.

**CLI implementation.** `TerminalPostCreateApproval` prompts on `System.console()` with `Run these commands? [y/N]`; without a console it returns `Skip`. The hook commands always use a `SkippingPostCreateApproval`, since their stdin is the hook's JSON and their stdout is read by Claude Code or Herdr. Skips print one note on stderr: `shogun: skipped N post-creation commands not approved on this machine; run 'shogun trust' in <repo> to review them`.

**`shogun trust`.** New `CliCommand.Trust(path, yes, revoke)`. Prints each command as a shell-quoted line, its approval state, and prompts like `new`. Exit 0 in every non-error case; 1 on IO errors. Added to the usage text and `docs/guide/cli.md`.

**Desktop implementation.** `DialogPostCreateApproval` suspends on a `CompletableDeferred` that the create dialog (and any provisioning action) resolves from a new "These commands will run" step listing the commands in a monospaced block, with "Run and remember" and "Skip". The creation stays in progress while the step is shown. Saving `hooks.postCreate` from `RepositoryConfigSection` records the approval for the saved list. Project settings shows "Approved on this machine" or "Not approved" next to the commands and a "Revoke" action.

**Reserved variable names.** A fixed list in `RepositoryConfigRules`: `PATH`, `HOME`, `SHELL`, `IFS`, `ENV`, `BASH_ENV`, `ZDOTDIR`, `PROMPT_COMMAND`, `NODE_OPTIONS`, `NODE_PATH`, `PYTHONPATH`, `PYTHONSTARTUP`, `PYTHONHOME`, `RUBYOPT`, `RUBYLIB`, `PERL5OPT`, `PERL5LIB`, `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, `JDK_JAVA_OPTIONS`, `CLASSPATH`, and every name starting with `LD_`, `DYLD_` or `GIT_`. Variables added by providers (Gradle, emulator, simulator) are produced by ShogunAi, not declared, and are not affected. The settings UI shows the same validation message. Alternative: allow them behind the trust gate; rejected, there is no known legitimate use for a worktree-specific `PATH` that a project script can't do itself, and a smaller trust surface is easier to explain.

**Real-path containment.** A `FileManager.isInside(root, path)` check resolves `root.toRealPath()` and the nearest existing ancestor of `path` with `toRealPath()`, then requires the prefix. It's called before `copy`, `createSymbolicLink` and `writeText` of provisioned and environment files. `createSymbolicLink` keeps `deleteIfExists`, now only after the check. Errors map to `WorktreeError.SecretCopyFailed` (copies and links) and a new `WorktreeError.EnvironmentFileOutsideWorktree`.

**Dotenv quoting.** Escape backticks in the double-quoted branch of `dotenvValue`, and treat a value containing a backtick as non-plain. Single quotes everywhere were considered; rejected because dotenv parsers differ on single-quote escapes and the current double-quote form is already relied on by `docker compose` users.

**Launch command quoting.** A small `ShellQuoting` object in the domain with `posix(value)` (single quotes, `'\''` for embedded quotes; reuses the existing `shellQuote` from `ResolveWorktreeEnvironmentUseCase`, moved there) and `windows(value)` (double quotes). `CopyWorktreeLaunchCommandUseCase` takes the platform from the existing OS detection used by the shell preference.

**Option-like refs.** `RepositoryConfigRules`/the identifier validator rejects branch names and start points starting with `-` with `WorktreeError.InvalidInput`. Existence checks move from `git branch --list <b>` to `git show-ref --verify --quiet refs/heads/<b>`. `git branch -d` calls get `--` before the name where Git accepts it.

## Risks / Trade-offs

- [Existing users' hooks stop running silently in Herdr] → The note on stderr lands in Herdr's plugin log; release notes and `docs/agents.md` explain `shogun trust --yes`; the desktop app asks on the next creation.
- [A repository moved to another path asks again] → Acceptable; the prompt shows the same commands.
- [Real-path checks on Windows with junctions] → `toRealPath` resolves junctions; covered by a test on the Windows CI runner once `release-hardening` adds it, and by a unit test with a fake `FileManager` meanwhile.
- [Reserved names break a repository that declares `PATH` today] → The error names the variable; the guide explains why and suggests a wrapper script.

## Migration Plan

No data migration: an absent `trust.json` means nothing is approved. The first creation after upgrading asks once per repository.
