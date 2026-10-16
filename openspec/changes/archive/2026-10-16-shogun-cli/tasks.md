# Tasks

## 1. `core` support

- [x] 1.1 Move `BuildInfo` generation from `desktopApp` to `core` and verify the app still shows the version (`./gradlew :desktopApp:compileKotlin`) and `core` compiles with it
- [x] 1.2 Add `ResolveRepositoryUseCase` and `WorktreeError.RepositoryNotFound` and verify with tests for a main worktree, a linked worktree, a subfolder and a non-repository
- [x] 1.3 Extract the file copy shared by creation and add `ProvisionWorktreeUseCase`, and verify with tests for copying local and repository files, overwriting, the main worktree, an unknown path, and a failed copy that leaves the worktree in place
- [x] 1.4 Document both use cases and the new error in `docs/architecture/domain` (English and Spanish) and verify with `mkdocs build --strict`

## 2. `cli` module

- [x] 2.1 Add the `cli` module (`application` plugin, `shogun` name, version from `appVersion`) and the argument parser with its sealed `CliCommand`, and verify with parser tests for every command, flag, missing argument and unknown input
- [x] 2.2 Implement `ShogunCli` with repository and project resolution and `new` (new and existing branch, `--worktrees-root`), and verify with tests for output, exit codes, project lookup by canonical path and the missing worktrees folder
- [x] 2.3 Implement `provision`, `status` and `clean` and verify with tests for their output, exit codes, `--yes`, `--delete-branches` and partial failures
- [x] 2.4 Implement `help` and `--version` and verify with tests
- [x] 2.5 Document the CLI in a new `docs/architecture/cli` page and the module list in `overview` (English and Spanish, added to `mkdocs.yml`), and verify with `mkdocs build --strict`

## 3. Distribution

- [x] 3.1 Make `desktopApp`'s `main` run the CLI when it has arguments (ignoring `-psn_` arguments) and verify with `createDistributable` that the launcher runs `status`, `--version` and a failing command with the right output and exit codes, and that opening it without arguments shows the window
- [x] 3.2 Add the `binary` to the Cask template and the CLI zip job to the release workflow, and verify the template renders with the release job's substitution and the workflow passes `actionlint` (or a YAML parse if unavailable)
- [x] 3.3 Verify `./gradlew :cli:distZip -PappVersion=1.2.3` produces `shogun-1.2.3.zip` whose `bin/shogun` runs `status` and `--version`
- [x] 3.4 Document installation (Homebrew, Linux link, zip) and usage in `README.md`/`README.es.md`, the release process in `docs/process/release` (both languages), and remove the delivered roadmap items (both languages), and verify with `mkdocs build --strict`

## 4. Integration checks

- [x] 4.1 Run `./gradlew test` and verify every test passes
- [x] 4.2 Build `packageReleaseDmg`, and with its launcher linked as `shogun` run `new`, `provision` (on a worktree made with `git worktree add`), `status` and `clean` (with and without `--yes`) against a throwaway repository, and verify results and that no window or Dock icon appears
- [x] 4.3 Run `openspec validate shogun-cli --strict` and verify it passes

## Workflow follow-up

- Archive the change (`/opsx:archive`) in the same branch before opening the pull request to `epic/pivot`.
