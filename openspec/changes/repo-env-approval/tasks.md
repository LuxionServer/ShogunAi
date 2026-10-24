# Tasks

## 1. Declaration rules (core)

- [ ] 1.1 Extend `isReservedVariableName` in `WorktreeEnvironment.kt` to the case-insensitive exact names, prefixes (with the `NODE_ENV` exception) and suffixes of design D4; verify with `WorktreeEnvironmentTest` cases for every name in the spec's "Names that change other tools" and "Reserved name in another case" scenarios, and that `PORT`, `DATABASE_URL`, `NODE_ENV`, `VITE_API_URL`, `ANDROID_SERIAL` stay valid
- [ ] 1.2 Reject templates with Cc/Cf characters or U+2028/U+2029 in `parseEnvironmentVariable` with a new `RepositoryConfigIssue.InvalidVariableValue` naming the variable, and keep the declared template text in `EnvironmentVariable.source`; verify with `WorktreeEnvironmentTest` (line break, ESC, carriage return, U+202E) and `RepositoryProvisioningFilesTest`
- [ ] 1.3 Apply the same character rule to `envFile` in `RepositoryProvisioningFiles.environmentOf`, and check Compose `portVariables` against the new reserved set; verify with `RepositoryProvisioningFilesTest` and the Compose provider tests
- [ ] 1.4 Update `docs/guide/configuration.md` and `.es.md` (reserved name families, character rule) and verify both pages say the same

## 2. Approval subject and trust store (core)

- [ ] 2.1 Add `RepositoryApprovalSubject` (commands, variables as name and template, envFile) and a helper that builds it from `RepositoryProvisioning`; change `RepositoryTrust`, `RepositoryTrust.NONE` and `FakeRepositoryTrust` to take it, with an empty subject counting as approved; verify the code compiles in every module (`./gradlew compileKotlin compileTestKotlin`)
- [ ] 2.2 Update `JsonRepositoryTrust`: digest identical to today's for subjects without variables or envFile, canonical object with variables sorted by name otherwise, `Approval` with defaulted `env` and `envFile`; verify with `JsonRepositoryTrustTest` cases for an old `trust.json` still approving commands, a changed template, an added variable, a changed `envFile`, reordered `env` keys staying approved, and reordered commands not
- [ ] 2.3 Rename `PostCreateApproval`/`PostCreateDecision` to `RepositoryApproval`/`ApprovalDecision` taking the subject, and update every implementation and call site; verify `./gradlew compileKotlin compileTestKotlin` passes

## 3. Gate the environment (core)

- [ ] 3.1 Give `ResolveWorktreeEnvironmentUseCase` a `RepositoryTrust` (default `NONE`) and leave out declared variables, their ports and their precedence over providers while the subject isn't approved, returning `awaitingApproval` and `declarationApproved` in `WorktreeEnvironment`; verify with `ResolveWorktreeEnvironmentUseCaseTest` for the "Declared variables apply only once approved" scenarios (approved, not approved, provider variables, declared override)
- [ ] 3.2 Make `WriteEnvironmentFileUseCase` write nothing when the declaration isn't approved; verify with `PrepareWorktreeEnvironmentUseCaseTest` (approved writes, not approved writes nothing, `envFile` without `env`)
- [ ] 3.3 Move the approval before environment preparation in `WorktreeCreation.kt` and `ProvisionWorktreeUseCase` (settle, prepare, run commands only if approved) and add `Worktree.skippedVariables`; verify with `CreateWorktreeUseCaseTest` and `ProvisionWorktreeUseCaseTest` for approve during creation, skip during creation (no port, no file, names skipped), and a single question for commands and variables
- [ ] 3.4 Pass the trust store to every resolver built in `ShogunCli` and `AppContainer`, and update tests that rely on declared variables (`OpenWorktreeInTerminalUseCaseTest`, `OpenWorktreeInHerdrUseCaseTest`, `GroupStateAndContextTest`, `PoolHarness` and the others listed by `grep -rl EnvironmentDeclaration`) to approve their repository; verify `./gradlew :core:test` passes
- [ ] 3.5 Update `docs/architecture/domain.md`, `docs/architecture/infrastructure.md` and their `.es.md` (trust subject, gating in the resolver, approval order) and verify both languages match

## 4. Command line

- [ ] 4.1 `shogun hook session-start`: deliver environment only for registered projects, build the workflow context without reading `.shogunai.json` for unregistered ones, name variables without values, add the awaiting-approval line; verify with `HookCommandsTest` for "Session in a worktree", "Repository not registered", "Variables awaiting approval" and "Invalid declaration"
- [ ] 4.2 `shogun trust` (text and `--json`): show commands, variables with templates and `envFile`, approve and revoke the whole subject, "nothing to approve" only for an empty subject; verify with `ShogunCliTest` for "Variables to review", "Variables as JSON", "Approve from a script" and "Nothing to approve"
- [ ] 4.3 `shogun new`/`provision`: print variables and `envFile` in the prompt before preparing the environment, name skipped variables in the stderr note, add `skippedVariables` to the JSON result; verify with `ShogunCliTest` for "Unapproved variables on a terminal", "Unapproved variables without a terminal", "Unapproved variables" (JSON) and "Nothing skipped"
- [ ] 4.4 `shogun env`: print only approved declared variables and the awaiting note on standard error; verify with `EnvironmentCommandsTest` for "Variables awaiting approval" and the existing scenarios with an approved repository
- [ ] 4.5 Update `docs/guide/cli.md`, `docs/architecture/cli.md`, `docs/agents.md` and their `.es.md` (`shogun trust` output, hook context, registration requirement) and verify both languages match

## 5. Desktop app

- [ ] 5.1 Approval dialog (`DialogRepositoryApproval` and its UI in `CreateWorktreeDialog`, `NewChangeDialog`, `WorktreeBoard`, `AppShell`): list commands, variables with templates and the environment file; report skipped variables; verify with the dialog's UI tests and `ProjectWorktreesTest`
- [ ] 5.2 Project settings: approval row for the whole subject with approve and revoke, save approving the new subject only when the old one was approved or declares no commands, editor errors for reserved names and control characters; verify with `ProjectConfigViewModelTest` for "Edited in settings", "Save with unapproved commands", "Changed outside the app", "Approve from settings", "Reserved name" and "Line break in a template"
- [ ] 5.3 Worktree detail: list `awaitingApproval` names without values and leave them out of the copied dotenv; verify with `ProjectWorktreesTest` or the detail pane's UI test for "Variables awaiting approval"
- [ ] 5.4 Add the new texts in English and Spanish and update `docs/architecture/ui.md` and `.es.md`; verify the localization tests pass

## 6. Integration

- [ ] 6.1 Run `./gradlew test` (CI doesn't run on the private advisory fork) and verify it passes with no compiler warnings
- [ ] 6.2 Reproduce the report's scenario with the installed CLI (`./gradlew :cli:installDist`) and an isolated `user.home` in the scratchpad: a repository declaring `XDG_CONFIG_HOME` is rejected as reserved, and a repository declaring an allowed variable writes nothing to `CLAUDE_ENV_FILE` while unregistered or unapproved and delivers it only after `shogun trust --yes`
- [ ] 6.3 Check the app manually: create a worktree in a repository with unapproved `env`, skip, see the awaiting variables in the detail, approve from project settings, and see them delivered when opening a terminal

## Workflow follow-up

- Keep the branch on the private advisory fork only (`git push ghsa-env fix/repo-env-approval`); never push to `origin`.
- Archive the change (`/opsx:archive`) after validation and before the pull request, updating `docs/roadmap.md` and `.es.md` only if an item is affected.
- Rebase on the fork's `develop`, re-run `./gradlew test`, and open the pull request on the fork: `gh pr create --repo LuxionServer/ShogunAi-ghsa-247f-242j-9wcr --base develop --assignee CiroFeliu`.
