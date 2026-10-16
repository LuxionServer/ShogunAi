# Design

## Context

`ProjectConfig` (in `~/.shogunai/projects.json`) holds the repository path, worktrees root, agent launch config, main branch and `secretFiles`: exact paths, relative to the base repository or absolute. Both creation use cases check that every secret file exists before `git worktree add`, then `addWorktreeAndCopySecrets` copies them and removes the worktree if a copy fails. `FileManager.copy` doesn't create parent folders. The project form edits `secretFiles` in memory and saves the whole `Project` on "Save". Since `extract-core-module`, all of this except the UI lives in `core`, which the CLI will reuse.

## Goals / Non-Goals

**Goals:**

- A repository-level, versioned description of what a worktree needs, shared by the desktop app now and by the CLI and agent hooks later.
- Folder and glob patterns, with the same semantics as `.worktreeinclude`.
- An explicit, opt-in path from today's local `secretFiles` to `.shogunai.json`.

**Non-Goals:**

- Post-creation hooks, symlinking secrets, detecting drifted secrets, Android/KMP providers: later changes extend `.shogunai.json` with new fields.
- Provisioning worktrees the app didn't create (that's the CLI's `shogun provision`).
- Matching Claude Code's rule that `.worktreeinclude` only copies gitignored files exactly: untracked files that match are copied too (see below).

## Decisions

### Git resolves the patterns

The files to copy are `git ls-files --others --ignored -z -x <pattern>…` run in the base repository, one `-x` per pattern. Without `--exclude-standard`, `--ignored` means "matched by the given patterns", so the output is every untracked file matching a pattern, ignored by `.gitignore` or not, with paths relative to the root. Git applies exactly the ignore-pattern syntax (anchoring with `/`, `**`, trailing `/` for folders, `!` negation), which is the syntax `.worktreeinclude` uses, and it lists files inside matched folders. `-z` keeps paths with spaces or non-ASCII characters intact. It runs as argv through `ShellCommandExecutor`, like every other Git call, and takes milliseconds on this repository.

Alternatives: Java's `PathMatcher` globs (different syntax from `.worktreeinclude`, no anchoring or negation rules, and a hand-written tree walk) or a gitignore library (a new dependency). Both reimplement what Git already does.

Untracked files that aren't ignored are included, unlike Claude Code's `.worktreeinclude` (ignored only). A file a user explicitly matches but hasn't committed or ignored is still something the worktree lacks, and the difference only shows for files nobody would list.

### `.shogunai.json` format

```json
{ "copy": ["local.properties", ".env*", ".idea/runConfigurations/"] }
```

A top-level object so later changes add fields (`hooks`, `link`, providers) without breaking old readers: decoding uses `ignoreUnknownKeys`. Missing file → no patterns. A file that isn't valid JSON or whose `copy` isn't a list of strings → `WorktreeError.InvalidRepositoryConfig(path, detail)`, raised during the pre-checks so nothing is created. Silently ignoring a broken file would create worktrees without their secrets.

### Model and port

- `RepositoryProvisioning(copyPatterns, worktreeIncludePatterns)` in `domain/model`, with `allPatterns` (distinct union).
- Port `RepositoryProvisioningSource` in `domain/io`: `read(repositoryPath): RepositoryProvisioning` (throws `InvalidRepositoryConfig`) and `addCopyPatterns(repositoryPath, patterns)`.
- `RepositoryProvisioningFiles` in `infrastructure` implements it on `java.nio.file` and `kotlinx.serialization`. `addCopyPatterns` reads the file as a `JsonObject`, replaces only `copy` (existing patterns first, new ones appended, no duplicates) and writes it back pretty-printed and atomically, so unknown fields survive. It refuses to touch a file it can't parse.

A separate port rather than `FileManager` methods: the use cases deal with patterns, not bytes, and the fake stays trivial.

### Where it plugs into creation

A new internal `resolveFilesToCopy(config, executor, provisioningSource)` returns the relative paths of the repository's provisioning (empty without patterns, no Git call). Both creation use cases call it in their pre-checks, after the existing local-files check, and pass the list to `addWorktreeAndCopySecrets`, which copies local files and then the resolved files with the existing rollback. A `git ls-files` failure is a `GitCommandFailed`, also before creation. Both use cases take the new port in their constructors; `WorktreeUseCases`/`AppContainer` wire it.

Local files keep their strict check (`SecretFileNotFound` when missing): they're exact paths the user typed. Patterns are lenient: no match, nothing copied.

`NioFileManager.copy` creates the destination's parent folders, needed for ignored folders the checkout doesn't create.

### Moving local files

`ProjectConfigViewModel` gets the port, reads the provisioning of `baseRepositoryPath` on start and whenever the path changes (cheap synchronous reads of two small files), and exposes it as loaded, missing, or unreadable. `moveLocalFilesToRepository()` takes the local entries that are relative and don't start with `..`, turns each into an anchored pattern (`/` + path with `/` separators), adds them to `pendingRepositoryPatterns` and removes them from `secretFiles`. Pending patterns count as an unsaved change. `save()` first calls `addCopyPatterns` when there are pending patterns; if it throws, save returns a failure and the project isn't saved, so the two files never disagree. Anchoring keeps today's meaning (`local.properties` was the root file, not any `local.properties` in a subfolder).

`save()` becomes `Result<Project>`; the screen shows the error and stays open on failure.

### Naming

`ProjectConfig.secretFiles` keeps its name and JSON key: no migration of `projects.json` and nothing to break for older versions reading the file. The UI calls it "Local files".

## Risks / Trade-offs

- [`git ls-files -o` walks untracked folders such as a large `node_modules`] → It's a single native call and only runs when the repository declares patterns; acceptable for creation, which already runs `git worktree add`.
- [A pattern matches more than intended, e.g. `*.json`, and copies many files] → The configuration screen lists the patterns; the copied paths are those Git reports, so the user can check with the same command.
- [Writing into the user's repository] → Only through the explicit move plus Save; the file is pretty-printed and other fields are kept so the diff is minimal.
- [Two sources of truth for the same file] → A file listed both locally and by a pattern is copied twice to the same place; harmless, and the move action exists to remove that duplication.

## Migration Plan

No automatic migration. `projects.json` keeps loading as is. Users who want their repository to carry the configuration use "Move to .shogunai.json" and commit the file. Rollback: reverting leaves `.shogunai.json` files in repositories, which older versions ignore.
