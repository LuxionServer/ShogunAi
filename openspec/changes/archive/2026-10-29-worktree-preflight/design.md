# Design

## Context

Every creation flow (`shogun new`, the desktop app's "New worktree", Claude Code's `shogun hook worktree-create`, and the creation of each repository of a multirepo group) goes through `CreateWorktreeUseCase` or `CreateWorktreeFromBranchUseCase`, which share `WorktreeCreation.kt`:

1. `checkProvisioningAndResolveFiles` fails with `SecretFileNotFound` when an exact entry of the project's local files doesn't exist (`FileManager.exists`, which follows links), then `resolveProvisionedFiles` lists the files to copy and link: exact entries, the files of local folders (`FileManager.listTree`) and the untracked files matched by patterns (`git ls-files --others --ignored -x …`) of the project's local files, `.shogunai.json` and `.worktreeinclude`.
2. `addWorktreeAndCopySecrets` runs `git worktree add`, then copies and links. Any copy error becomes `SecretCopyFailed` and rolls the worktree back.

So a pattern-matched file that is a broken link or unreadable, or an exact entry that exists but can't be read, is only found after `git worktree add`, and the error names no file. `NioFileManager.listTree` ignores entries it can't read (`visitFileFailed` continues), so their files are silently left out. `ProvisionWorktreeUseCase` (`shogun provision`, Herdr's and Orca's provisioning) goes through the same resolution and copy, minus the rollback.

The demo failure on another Mac was investigated in the `login-shell-path` change (`openspec/changes/archive/2026-10-29-login-shell-path/design.md`): its most likely cause was Claude Code's hooks not being installed. That can't be confirmed without that Mac, so this change closes the half-way failure the code itself allows: a local file found while resolving but unusable when copied.

## Goals / Non-Goals

**Goals:**

- One pass over every resolved local file, before any Git command that creates something, reporting all problems at once.
- One typed error carrying structured data (file, full path, reason, origin), so the CLI's message and the app's localized texts are built from the same facts.

**Non-Goals:**

- Checking the destination (the worktree folder being writable, free disk space): failures there stay `SecretCopyFailed` with its rollback.
- Making multirepo group creation atomic across repositories: each repository's creation is checked before it starts, but a group whose second repository fails keeps the first one's worktree, as today. Follow-up below.
- `shogun doctor` / "Check installation" and the host's `PATH` or environment (other changes).

## Decisions

### A readability check on the `FileManager` port

`FileManager` gains `unavailableReason(path): UnavailableReason?`, `null` when the path can be read. `UnavailableReason` is a sealed domain model: `Missing`, `BrokenLink(target)` and `Unreadable(detail)`. `NioFileManager` decides it in this order: nothing at the path without following links → `Missing`; a symbolic link whose target doesn't exist → `BrokenLink` with `Files.readSymbolicLink`'s target; a directory → readable when it can be listed; a file → readable when it can be opened (`Files.newByteChannel` opened and closed), otherwise `Unreadable` with the exception's reason.

Opening the file rather than calling `Files.isReadable` is deliberate: `access(2)` can say yes while macOS's privacy controls (TCC) deny the actual read, which is the kind of difference that only shows up on another machine. Opening is cheap and reads no bytes.

Alternatives: checking with `exists` only (already done, misses unreadable files and links found by patterns); copying everything into a staging folder first (doubles the I/O and still needs a rollback).

### `DirectoryListing` reports what it couldn't read

`DirectoryListing` gains `unreadable: List<String>`, filled from `visitFileFailed` with the entry's path relative to the folder. The preflight treats those entries of copied local folders as `Unreadable`. Linked local folders aren't walked for this: the link points at the base folder, whatever is in it.

### The preflight in `WorktreeCreation.kt`

A new internal function, `checkLocalFilesAndResolve`, replaces `checkProvisioningAndResolveFiles` in the two creation use cases and in `ProvisionWorktreeUseCase`:

1. Exact entries that don't exist (`exists`, as today) → `Missing`, origin project.
2. `resolveProvisionedFiles` as today. `ProvisionedFiles` gains `repositoryFiles` (files that came only from `.shogunai.json` or `.worktreeinclude`, to tell the origin) and `unreadableInFolders`.
3. Every file to copy or link (except those already reported missing), every file of a copied folder, and every unreadable folder entry is checked with `unavailableReason` on its base path.
4. Any problem → `WorktreeError.LocalFilesUnavailable(basePath, files)`, before `git worktree add`.

`CheckProvisionedFilesUseCase` (the drift comparison) keeps the old, cheaper check of missing exact entries only, now throwing `LocalFilesUnavailable`: it reads, it doesn't act, and its own report already calls an unreadable or different file "differs".

The repository's patterns stay resolved by a single `git ls-files` call (existing behaviour and tests), so the origin distinguishes the project's local files from the repository's files as a whole, and the fix names both `.shogunai.json` and `.worktreeinclude`.

### One error, replacing `SecretFileNotFound`

`WorktreeError.LocalFilesUnavailable(basePath, files: List<UnavailableLocalFile>)`, with `UnavailableLocalFile(path, fullPath, origin, reason)` and `LocalFileOrigin { PROJECT, REPOSITORY }`. It replaces `SecretFileNotFound` rather than living next to it: a missing file is one more reason, and two errors for "the local files aren't ready" would split the CLI's and the app's handling for no gain.

Its message, which the CLI prints as is, is one header line saying that nothing was changed and one indented line per file with what is wrong, the full path and the fix for its reason and origin. The app builds its own texts from the same fields: a summary naming the files and the base folder, a next step assembled from the reasons present, technical details with one line per file, and the existing "Open project settings" fix.

### The regression test

Written first, with a real Git repository in a temporary folder and `NioFileManager`: `.shogunai.json` copies `*.properties` and the base folder holds `keys.properties` as a link to a file that doesn't exist. Before the fix, creation runs `git worktree add` and fails with `SecretCopyFailed` (no file named); after it, creation fails with `LocalFilesUnavailable` naming `keys.properties` as a broken link, and no worktree folder or branch exists. A broken link is used rather than a permission change because it reproduces the same half-way failure on every platform and when tests run as root.

### What `shogun doctor` can reuse

The later doctor change reports "each project's declared local files present in its base folder". It can call `checkLocalFilesAndResolve`'s two halves without creating anything: `resolveProvisionedFiles` plus the availability check (to be exposed as a function returning `List<UnavailableLocalFile>` instead of throwing), per registered project, and render each `UnavailableLocalFile` with the same per-reason fixes. The check needs only the project config, the repository's provisioning file and read access; it changes nothing.

## Risks / Trade-offs

- [Opening every file to copy adds I/O before creation] → One open and close per file, no reads; local files are few. Folders with many files cost one open each, still far less than the copy that follows.
- [A file can become unavailable between the check and the copy] → The copy keeps its existing error and rollback; the preflight narrows the window, it doesn't remove it.
- [Errors that used to be `SecretCopyFailed` now fail earlier with a different type] → Callers only display it; the app maps the new type to its own texts and fix.
- [Checking `shogun provision` too] → Same resolution and copy as creation; failing before deleting or copying is strictly safer there.

## Follow-ups

- Group creation that checks every repository of the group before creating any (multirepo groups).
- `shogun doctor` and "Check installation" reusing the availability check (roadmap item 1, second bullet).
