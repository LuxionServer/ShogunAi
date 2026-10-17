# Proposal

## Why

Every worktree that builds an Xcode project gets its own DerivedData folder (Xcode names it after a hash of the project's path), so parallel worktrees don't clash, but nothing ever removes them: Xcode keeps the folder of a deleted worktree forever, and each one is typically several gigabytes for a real app. And each new worktree starts from a cold build. Xcode 27 can share compilations between paths: in a spike with a 150-file `.xcodeproj` in two folders, enabling compilation caching with prefix mapping gave the second folder 60 cache hits and no misses, cutting its first build from 16.5 s to 1.8 s, and the settings could be applied from the environment through `XCODE_XCCONFIG_FILE` without touching the project. Without prefix mapping there was almost no reuse (8 hits of 86), and Swift packages got none.

## What Changes

- `.shogunai.json` accepts `"providers": { "xcode": {} }`, optionally `{ "compilationCache": true }`. It only has an effect on macOS; without the provider nothing Xcode-related is read, shown or run.
- DerivedData is found in Xcode's default location (`~/Library/Developer/Xcode/DerivedData`) or the custom absolute location set in Xcode's preferences. Only folders whose `info.plist` records a `WorkspacePath` count; Xcode's shared caches at the root (`CompilationCache.noindex`, `ModuleCache.noindex`, …) are never listed or touched.
- Each folder is attributed by its `WorkspacePath` to a worktree of the repository, to another project, or marked orphaned when that path no longer exists. A path on a volume that isn't mounted is not orphaned.
- Conservative deletion: ShogunAi only deletes orphaned folders, anywhere on the machine, never the DerivedData of a project that still exists.
- Desktop app: an "Xcode DerivedData" section on the worktree screen with each worktree's folder size, the orphans' count and size with a "Delete orphans" action, and, with compilation caching, the size of the shared compilation cache. The cleanup dialog offers, checked by default, to delete the orphaned DerivedData, including that of the worktrees it removes.
- CLI: `shogun xcode` lists the folders and orphans, `shogun xcode clean` deletes the orphans, and `shogun clean --delete-derived-data` does it as part of a cleanup.
- With `"compilationCache": true`, ShogunAi writes a machine-level `.xcconfig` that turns on compilation caching and Swift and Clang prefix mapping, and every worktree's environment gains `XCODE_XCCONFIG_FILE` pointing to it, delivered like any other variable (`shogun env`, the `SessionStart` hook, `envFile`). A variable with that name declared in `env` takes precedence.
- Docs: README section for Xcode repositories, agents page, architecture pages, both languages; the DerivedData roadmap bullet is removed when the change is archived.

## Capabilities

### New Capabilities

- `xcode-provider`: finding and attributing Xcode DerivedData, orphan detection and deletion, the worktree screen section, and compilation caching delivered through the worktree environment.

### Modified Capabilities

- `repository-provisioning`: the `xcode` provider declaration and its validation; the unknown-provider example no longer uses `xcode`.
- `worktree-cleanup`: the option to delete orphaned DerivedData after removing worktrees.
- `command-line-tool`: `shogun xcode`, `shogun xcode clean` and `shogun clean --delete-derived-data`.

## Impact

- `core`: `XcodeProvider` declaration, a `DerivedDataSource` port (location from Xcode's preferences, folders with their `info.plist`, sizes, deletion, mounted volumes) and its macOS implementation, use cases to list, attribute and delete, the compilation cache `.xcconfig` and its environment contribution.
- `cli`: `xcode` command, `clean --delete-derived-data`, help.
- `desktopApp`: worktree screen section, cleanup option, strings in English and Spanish.
- Repositories without the provider, and other operating systems, see no change; older versions ignore the new fields.
