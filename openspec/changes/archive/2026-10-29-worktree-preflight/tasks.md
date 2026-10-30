# Tasks

## 1. Regression test first

- [x] 1.1 Add a core test with a real Git repository in a temporary folder and `NioFileManager`: `.shogunai.json` copies `*.properties`, `keys.properties` is a link to a missing file; creating a worktree must fail with `LocalFilesUnavailable` naming it as a broken link and leave no worktree folder or branch. Verify it fails on the current code (half-way: `git worktree add` runs, then `SecretCopyFailed`).

## 2. Availability on the file port

- [x] 2.1 Add `UnavailableReason` (`Missing`, `BrokenLink`, `Unreadable`), `LocalFileOrigin`, `UnavailableLocalFile` and `WorktreeError.LocalFilesUnavailable`, replacing `SecretFileNotFound` everywhere (core, desktop texts, tests). Verify: `./gradlew :core:compileKotlin` without warnings.
- [x] 2.2 Add `FileManager.unavailableReason` and implement it in `NioFileManager` (missing, broken link, directory listing, file opening) and in `FakeFileManager` (configurable). Verify: `NioFileManagerTest` cases for a readable file, a missing path, a broken link and, on POSIX when not root, a file without read permission.
- [x] 2.3 Add `DirectoryListing.unreadable`, filled by `NioFileManager.listTree` from entries it couldn't visit. Verify: `NioFileManagerTest` with an unreadable subfolder (POSIX, not root).

## 3. Preflight before acting

- [x] 3.1 Track in `ProvisionedFiles` which files come only from the repository's patterns and the unreadable entries of copied folders. Verify: `ResolveProvisionedFilesTest`.
- [x] 3.2 Add the preflight (missing exact entries, then every file to copy or link and every file of a copied folder checked with `unavailableReason`, all reported at once) and use it in `CreateWorktreeUseCase`, `CreateWorktreeFromBranchUseCase` and `ProvisionWorktreeUseCase`; keep the missing-entries-only check in `CheckProvisionedFilesUseCase`. Verify: use case tests that no `git worktree add` runs and nothing is copied for a broken link, an unreadable file, an unreadable folder entry and several problems at once, that origins are right, and that a pattern matching nothing still passes; the regression test of 1.1 passes.
- [x] 3.3 Write the error's message: a header saying nothing was changed and one line per file with its reason, full path and fix by reason and origin. Verify: a `WorktreeError` message test, and a CLI test of `shogun new` printing it.

## 4. Desktop app

- [x] 4.1 English and Spanish texts for `LocalFilesUnavailable` (summary naming the files and base folder, next step from the reasons present), technical details with one line per file, and the "Open project settings" fix. Verify: `StringsTest` / error description tests in both languages.

## 5. Validation

- [x] 5.1 `./gradlew test` passes for every module.
- [x] 5.2 Check it for real with the built CLI on a temporary repository: a broken link matched by `.shogunai.json` and an unreadable local file make `shogun new` fail before creating anything with the expected message; fixing them makes it succeed with the files copied.

## Workflow follow-up

- Archive with `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md` (remove the "Checking before acting" bullet of item 1, keep the group follow-up and the doctor reuse note).
- Implementation commit, bring in `origin/develop`, open the pull request against `develop` assigned to CiroFeliu.
