# Design

## Context

`ProjectConfig.secretFiles` is a list of paths relative to the base repository (or absolute outside it). `checkProvisioningAndResolveFiles` verifies each exists and `resolveProvisionedFiles` merges them with the files matched by `.shogunai.json` into `ProvisionedFiles(copied, linked)`, a flat list of file paths. Every creation flow, `ProvisionWorktreeUseCase` and `CheckProvisionedFilesUseCase` consume that list, and `applyProvisionedFiles` copies file by file, guarded by `requireInsideWorktree`. `FileManager.copy` handles one file. The browse button uses `JFileChooser` in `FILES_ONLY` mode. Repository folder patterns already work because `git ls-files` expands them into files. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- A folder entry behaves, in every flow, as if each file under it had been listed individually.
- No change to the persisted format or to the downstream pipeline (`ProvisionedFiles`, drift check, rollback).

**Non-Goals:**
- Glob patterns in the local list (the repository file already offers them).
- Linking local folders, copying empty directories, or preserving symbolic links.
- Removing from the worktree files that no longer exist in the base folder.

## Decisions

**Copy, not link.** Local files are copied today, and the `link` field is a repository-level opt-in; a local folder is copied too. Linking would make secrets shared and writable from every worktree, which is a separate choice the user can make in `.shogunai.json` (`"link": ["keys/"]`). A local entry that the repository also declares in `link` keeps the current rule: it is copied.

**Expand folders into files at resolution time.** `resolveProvisionedFiles` replaces each folder entry by the relative paths of the regular files under it, and the rest of the pipeline sees only files. This keeps `applyProvisionedFiles`, the drift check, the reports and the rollback unchanged, and means a file added to the folder later is picked up without editing the entry. Alternative: make `FileManager.copy` recursive. Rejected: it would hide the file list from drift checks and reports and need a second containment check per nested file.

**Folder detection by `FileManager.isDirectory` on the base path, not by a trailing slash.** Entries are stored as the user typed or picked them; the UI strips a trailing separator when normalizing so `keys` and `keys/` are the same entry. A new `FileManager.listFiles(directory): List<String>` returns relative, `/`-separated paths of regular files, depth-first, hidden files included, symbolic links not followed and not listed.

**Existing folder in the worktree is merged.** Each file is copied with the existing replace-existing behavior; files only present in the worktree are kept. This matches provisioning of exact files and avoids deleting anything. Tracked files inside the folder are overwritten by the local copy: an explicit local entry wins, as it does for an exact file today.

**Hidden files are copied; symbolic links are skipped.** Secret folders typically hold dotfiles, so no name is filtered. Not following links prevents a copy escaping the folder or looping, and the destination check `requireInsideWorktree` still runs per expanded file.

**Missing folder is `SecretFileNotFound`.** The existence check already runs before any Git command; a folder is a path that exists, so the check and the error stay as they are. A path that exists but is neither a regular file nor a folder is treated as a file.

**Browse accepts both.** `JFileChooser` switches to `FILES_AND_DIRECTORIES` with multi-selection, keeping hidden files visible. Results go through the existing relative-to-base normalization. The text field accepts a folder path the same way.

## Risks / Trade-offs

- [A very large folder (e.g. `node_modules`) makes creation slow or heavy] → documented in the UI description; copy cost is the user's choice, as with a `copy` pattern.
- [Files removed from the base folder stay in older worktrees] → out of scope; the drift check only reports missing or different files.
- [A folder that is replaced by a file (or the reverse) between saves changes how the entry is read] → the kind is read on every run, so the entry follows what is on disk.
- [Skipped symbolic links surprise a user who keeps links in the folder] → skipped silently; a later change can report them.
