# Tasks

## 1. Core: shared variables and workspace writes

- [ ] 1.1 Add the pure `renameSharedAlias(env, old, new)` (moves the alias's entry and rewrites `${port:old.<key>}` references, skipping `$$` escapes) and a function listing the variables that reference an alias; verified by unit tests for a moved entry, a rewritten reference, a `$${port:old.key}` literal left alone and an `env` without the alias.
- [ ] 1.2 Add `setSharedVariable`, `unsetSharedVariable` and `setSharedVariables` to `ConfigureMultirepoUseCase`, validating the resulting block with `parseSharedEnvironment`, dropping empty aliases and an empty `env`, and raising `UnknownMultirepoAlias` for an unknown alias; verified by `ConfigureMultirepoUseCaseTest` cases for add, replace, invalid template, unknown alias, unset of the last variable, unset of an undeclared variable, an unapproved multirepo, and the result staying approved.
- [ ] 1.3 Make `update` and `addRepository` refuse when `env.problems` isn't empty; verified by a test where setting the name of a declaration with a non-string `env` value fails naming it and leaves the file unchanged.
- [ ] 1.4 Carry `env` in `renameRepository` and make `removeRepository` drop the alias's entry and refuse while referenced; verified by tests for "Shared variables follow renamed repositories" and "Removing a repository keeps shared variables valid".
- [ ] 1.5 Add `detectWorkspace` (removes `workspace`, refuses a non-repository folder, a `.` entry in `repos` and a taken `workspace` alias, no-op when already detected, renames `env` from a declared alias); verified by `WorkspaceRepositoryTest` cases for every new scenario of "Show and declare the workspace repository".

## 2. Command line

- [ ] 2.1 Parse `multirepo env`, `env set <alias> <name> <template>`, `env unset <alias> <name>` and `workspace detect` in `CliCommand.kt`; verified by `ParseArgumentsTest` cases for each form and for wrong argument counts.
- [ ] 2.2 Handle them in `ShogunCli` (listing allowed while the multirepo needs review, writes refused; text and `--json` output; already-detected and default-left messages for `workspace detect`) and document them in `CliHelp.kt`; verified by `MultirepoCommandsTest` cases for the scenarios of "List/Set/Unset a shared variable from the command line", "Edit a shared variable" and "Shared variables edited in an unapproved multirepo", `remove-repo` of a referenced repository, and `workspace detect`.
- [ ] 2.3 Update `docs/guide/multirepo.md` and `docs/guide/multirepo.es.md` (editing shared variables with `shogun multirepo env`, the rename and removal rules, `workspace detect`); verified by `mkdocs build --strict`.

## 3. Desktop app

- [ ] 3.1 Add `setSharedVariables` and `detectWorkspace` to `MultirepoSetup` and `AppContainer`'s implementation; verified by `MultirepoSetupTest` cases that write through them.
- [ ] 3.2 Add shared variable drafts to `MultirepoSettingsState` (rows from `sharedTemplates`, per-row validation including duplicate names, `hasChanges`, `canSave`, saving through `setSharedVariables`, the unrepresentable `env` problem) and `returnWorkspaceToDetection`; verified by state tests for add, remove, invalid row blocking save, and returning to detection.
- [ ] 3.3 Add the "Shared variables" section and the "Return to detection" button to `MultirepoSettingsView`, with English and Spanish strings; verified by a desktop UI test that adds `API_URL` for `app`, saves and finds it in the declaration, and one that returns a turned-off workspace to detection.
- [ ] 3.4 Mention the settings editing in the "In the desktop app" part of both multirepo guides; verified by `mkdocs build --strict`.

## 4. Validation

- [ ] 4.1 Run `./gradlew test` and check it passes with no warnings.
- [ ] 4.2 In a scratch multirepo with two scratch repositories: `shogun multirepo env set` an `API_URL` referencing the other repository, check `shogun env` in a worktree receives it without a new `trust`, rename the referenced repository and check the reference follows, then `workspace clear` and `workspace detect` on a folder that is a repository.
- [ ] 4.3 Check in the running app, on the secondary display: add, edit and remove a shared variable and return the workspace to detection from the multirepo settings, and see the result in `shogun multirepo show`.

## Workflow follow-up

- Commit the planning (proposal, design, specs, tasks) once validated.
- Archive with `/opsx:archive` after validation, updating `docs/roadmap.md` and `docs/roadmap.es.md`: remove the shared variables and workspace items from point 4 and keep `shogun group add` as pending.
- Commit the implementation, bring the branch up to date with `origin/develop`, and open the pull request against `develop` assigned to CiroFeliu.
