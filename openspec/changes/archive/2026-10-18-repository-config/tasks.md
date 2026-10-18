# Tasks

## 1. Parse the new fields

- [x] 1.1 Add `linkPatterns` and `postCreateCommands` to `RepositoryProvisioning` and parse `link` and `hooks.postCreate` (list of argv lists or single-argv shorthand) in `RepositoryProvisioningFiles`, rejecting invalid values with `InvalidRepositoryConfig`; verify with `RepositoryProvisioningFilesTest` cases for every scenario of "Declare post-creation commands", "Invalid post-creation commands" and "Declare linked files"
- [x] 1.2 Verify that `addCopyPatterns` keeps `hooks` and `link` unchanged with a `RepositoryProvisioningFilesTest` case ("Keep the new fields when writing")
- [x] 1.3 Document `link` and `hooks.postCreate` in `docs/guide/configuration.md` and `docs/guide/configuration.es.md`, including the symbolic link sharing caveat and that commands run on creation and provisioning

## 2. Link files

- [x] 2.1 Add `createSymbolicLink(target, link)` (replacing an existing file, signalling unsupported links separately), `isSymbolicLinkTo(link, target)` and `hasSameContent(first, second)` to the `FileManager` port and `NioFileManager`; verify with `NioFileManagerTest` cases using temporary directories
- [x] 2.2 Resolve the provisioned files into linked and copied sets (`link` wins over `copy`, local files always copied) and apply them in `WorktreeCreation.kt`, returning entries with their mode and falling back to copy when links are unsupported; verify with use case tests for every scenario of "Link files instead of copying them" and "Copy when links aren't available", using a fake `FileManager`
- [x] 2.3 Update `ProvisionWorktreeUseCase` and its callers to the new result type, and make `shogun provision` mark linked files and copied-instead-of-linked files; verify with `ShogunCliTest` ("Report linked files when provisioning from the command line")

## 3. Post-creation commands

- [x] 3.1 Add `WorktreeError.PostCreateCommandFailed(worktreePath, command, exitCode, errorOutput)` and run the commands in order in the worktree after files and environment, in both creation flows and when provisioning, stopping at the first failure and keeping the worktree; verify with use case tests for every scenario of "Run post-creation commands", including a command that can't be started
- [x] 3.2 Make `shogun new` print the kept path on standard output and the failure on standard error with exit code 1, `shogun provision` exit 1, and `shogun hook worktree-create` print the failure on standard error and the path as the last line with exit code 0; verify with `ShogunCliTest` cases for each scenario of "Report a failed post-creation command from the command line" and "Keep Claude Code's worktree when a post-creation command fails"
- [x] 3.3 Map `PostCreateCommandFailed` to a message in `Strings.kt`, `EnglishStrings.kt` and `SpanishStrings.kt` saying the worktree was created but the command failed; verify `./gradlew :desktopApp:test` passes
- [x] 3.4 Document post-creation command failures for `shogun new`, `shogun provision` and the WorktreeCreate hook in `docs/guide/cli.md` and `docs/guide/cli.es.md`

## 4. Check provisioned files

- [x] 4.1 Add `CheckProvisionedFilesUseCase` reporting missing, differing and not-linked files without writing; verify with use case tests for every scenario of "Compare a worktree's provisioned files"
- [x] 4.2 Parse `shogun provision --check [<path>]`, print one line per reported file and exit 0, 3 or 1, and add it to the usage text; verify with `ParseArgumentsTest` and `ShogunCliTest` cases for every scenario of "Check a worktree's provisioned files from the command line"
- [x] 4.3 Document `shogun provision --check` and its exit codes in `docs/guide/cli.md` and `docs/guide/cli.es.md`

## 5. Integration

- [x] 5.1 Run `./gradlew test` and verify all modules pass
- [x] 5.2 In a scratch repository with `link`, `copy` and `hooks.postCreate`, run `shogun new`, edit a linked and a copied secret in the base repository, run `shogun provision --check`, and verify the copied one is reported and the linked one isn't
- [x] 5.3 Remove the "Configuración del repositorio" / "Repository configuration" section from `docs/roadmap.md` and `docs/roadmap.es.md`, and its node from the roadmap diagram

## Workflow follow-up

- Archive the change (`/opsx:archive`) after validation and before opening the pull request against `develop`.
