# Design

## Context

Provisioning lives in `core/.../domain/usecase/WorktreeCreation.kt`: `resolveFilesToCopy` asks `git ls-files --others --ignored -x <pattern>…` for the untracked files matching `copy` and `.worktreeinclude`, `copyProvisionedFiles` copies them together with the project's local files through the `FileManager` port, and environment preparation follows. Every flow goes through it: `CreateWorktreeUseCase`, `CreateWorktreeFromBranchUseCase`, `ProvisionWorktreeUseCase`, and from them the desktop app, `shogun new`, `shogun provision`, `shogun hook worktree-create` and `shogun hook herdr`. `RepositoryProvisioningFiles` parses `.shogunai.json` field by field and already ignores the `hooks` key that tests use as an example of an unknown field. `ShellCommandExecutor` runs argv and captures output; it has no environment parameter.

## Goals / Non-Goals

**Goals:**
- One place that applies copy, link, environment and hooks, so every creation and provisioning flow behaves the same.
- Keep the desktop app untouched apart from an error string, so this branch merges cleanly with `epic/ui-redesign`.

**Non-Goals:**
- Passing the worktree's environment variables (`env`, ports) to post-creation commands. A command that needs them can run `shogun env` itself; adding environment support to the executor port is a separate change.
- Streaming hook output live or timing out long commands.
- Showing drift in the desktop app or in `shogun status`; the check is CLI-only until the redesigned detail pane exists.
- Linking the project's local files (`~/.shogunai/projects.json`); they keep being copied.
- Other hook moments (pre-removal, post-checkout).

## Decisions

**`postCreate` is a list of argv lists, with a single-argv shorthand.** The roadmap's own examples are two commands (`npm install`, `direnv allow`), so one argv isn't enough, and a shell string would contradict the project rule of running argv. The shorthand keeps the common single-command case readable and is unambiguous: the first element is either a string or a list. Alternative considered: `["sh", "-c", "…"]` for several commands, rejected because it pushes users back to shell strings.

**A failed hook keeps the worktree and surfaces as a typed error.** The checkout, files and ports are already correct; removing everything because `npm install` hit a network blip would be the worst outcome. Creation returns `Result.failure(WorktreeError.PostCreateCommandFailed(worktreePath, command, exitCode, errorOutput))`, carrying the kept path, so callers that need the path (`shogun new`, the WorktreeCreate hook) read it from the error instead of changing the success type. Starting failures (`CommandExecutionException`) are wrapped in the same error. Alternative considered: returning a success with a warning field, rejected because every caller would have to remember to check it and the desktop app would show success.

**The WorktreeCreate hook exits 0 on a hook failure.** Claude Code aborts the session's worktree when the hook fails, which would strand a usable worktree; a warning on standard error is visible in its hook log. `shogun new` exits 1 instead, because a script calling it should notice, and still prints the path so it can recover.

**Hooks run last, after copy, link and environment.** A setup command such as `direnv allow` or a Gradle sync needs the secrets and `envFile` in place.

**Links are absolute symbolic links to the base repository's file, created per file.** Patterns are resolved with the same `git ls-files` call as `copy`, so folder patterns link each file inside, not the folder: a linked folder would also capture files the worktree writes into it. Absolute targets survive moving the worktrees folder independently of the base repository, which is the common case. `link` wins over `copy` for a file matched by both. When provisioning an existing worktree, the destination is deleted and replaced, consistent with copy overwriting.

**Fallback to copy only when links are unsupported.** `FileManager.createSymbolicLink` signals "unsupported" separately (`UnsupportedOperationException`, or a `FileSystemException` for the missing Windows privilege) from other I/O failures. Unsupported falls back to a copy and is reported as such; any other failure behaves like a failed copy (rollback during creation). Provisioning results change from a list of paths to a list of entries with a mode (copied, linked, copied instead of linking) so the CLI can mark them.

**Drift check as a use case returning typed states.** `CheckProvisionedFilesUseCase` resolves the same file list as provisioning and compares each against the worktree: missing, differs (byte comparison through a new `FileManager.hasSameContent`), not linked (a `link` file that isn't a symbolic link resolving to the base file). It never writes. `shogun provision --check` maps states to lines and exit code 3, distinct from 1 (error) and 2 (usage), mirroring `git diff --exit-code` style without colliding with existing codes.

**Parsing stays in `RepositoryProvisioningFiles`**, following the existing `copyPatternsOf`/`providersOf` style, with `RepositoryProvisioning` gaining `linkPatterns` and `postCreateCommands`. `allPatterns` keeps meaning "files to provision", and a new resolution step splits them into linked and copied sets.

## Risks / Trade-offs

- [A cloned third-party repository can declare commands that run when a worktree is created] → Same trust level as its `package.json` scripts or Claude Code's project hooks: the user chose to work in that repository and asked for the worktree. The commands are documented as running on creation and provisioning; a trust prompt can be added later if needed.
- [Long hooks block the desktop app's creation dialog and the CLI with no output until they finish] → Acceptable for setup commands; streaming is a non-goal for now and the error carries the full output.
- [A symbolic link to a secret lets a tool in one worktree overwrite the base repository's file] → That's the intended sharing; it's documented next to `link`, and `copy` remains the default.
- [Windows users get copies instead of links without noticing] → The fallback is reported in `shogun provision` output, and `--check` shows "not linked" afterwards.
- [Conflict with `epic/ui-redesign`'s `providers-configuration`, which also writes `.shogunai.json`] → It preserves unknown fields; this change adds a spec requirement that writers keep `hooks` and `link`. The only shared desktop files are the `Strings` files, a small merge.

## Migration Plan

No migration: both fields are optional and a repository without them behaves as before. Rollback is reverting the change; files already linked stay as symbolic links, which keep working.
