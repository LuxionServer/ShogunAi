# Tasks

## 1. Declaration, detection and approval

- [ ] 1.1 Read and write `providers.node` in the `.shogunai.json` reader and writer (`RepositoryProviders.node`), keeping unknown options; verify with tests for the `repository-provisioning` delta scenarios and that writing other providers keeps it
- [ ] 1.2 Add `Stack.NODE` and `SuggestedProvider.NODE("node")` with the mapping Node → `node`, and detect it in `DetectRepositoryStacksUseCase` from the main folder's `package.json` with its lockfiles as evidence; verify with tests for the Node repository, subfolder-only and suggestion scenarios
- [ ] 1.3 Make `shogun stacks` (text and JSON) list the Node stack and the `node` suggestion; verify with a CLI test on a temporary repository with `package.json` and `pnpm-lock.yaml`
- [ ] 1.4 Add `nodeInstall` to the approval subject (true when `node` is declared), counting in `isEmpty` and in the digest only when true, and show it in `shogun trust` text and JSON and the `shogun new`/`provision` question; verify with tests for the `repository-trust` delta scenarios, including that an existing approval without the provider keeps its digest

## 2. Node dependencies

- [ ] 2.1 Add the `NodeDependencies` model and a package manager resolver (lockfiles, `packageManager`, the pnpm/Yarn/bun/npm order, Yarn Berry by `.yarnrc.yml`, install commands and markers); verify with unit tests for every manager, several lockfiles with and without `packageManager`, and no lockfile
- [ ] 2.2 Add `PrepareNodeDependenciesUseCase`: skip when undeclared, `present` when `node_modules` exists, clone every ignored `node_modules` of the base with `cp -c -R` on macOS when locks match and the marker is not older than the lock (removing partial clones on failure), otherwise install when approved, or `needed` with its reason and command; verify with fake-executor tests for each `node-provider` clone, install, failure and cancellation scenario
- [ ] 2.3 Call it from `PrepareWorktreeEnvironmentUseCase` after pods and Flutter with the environment's approval, and carry the outcome to `Worktree.node` and `ProvisionResult.node`; wire it in `ShogunEngine`; verify with use case tests for the order before post-creation commands, the unapproved hook and the failure that keeps the worktree
- [ ] 2.4 Report the outcome in `shogun new`, `shogun provision` and `shogun hook worktree-create` as a text line and as `node.dependencies` in JSON, absent when the provider isn't declared; verify with CLI tests for each output

## 3. Doctor and agent context

- [ ] 3.1 Add `NODE` and `NODE_PACKAGE_MANAGER` provider tool checks with their fixes (bun checked without `node`, no lockfile only `node`); verify with `CheckProviderToolsUseCase` and report text tests for the `node-provider` and `installation-check` scenarios
- [ ] 3.2 Add the Node workflow rule (condition: Node provider; manager and lockfile names filled in) for `SessionStart` and `shogun guide resources`; verify with workflow rule tests for the pnpm and guide scenarios

## 4. Desktop app

- [ ] 4.1 Add the Node label, the suggestion's "Turn on" branch and the switch in "Repository configuration", carry `node` in `RepositoryConfigDraft`, show the Node install line in the approval views (English and Spanish strings), and pass the preparer in `AppContainer`; verify with the project configuration view model tests for the `project-configuration` delta scenarios and by checking the section with `shogun headless start`

## 5. Documentation

- [ ] 5.1 Write the Node section of `docs/guide/providers.md` and `providers.es.md` (managers and lockfiles, clone or install, approval, workspaces, ports with `env` and `${port}`, generated files after a clone) and add Node to the detection table; verify `mkdocs build --strict` passes

## 6. Integration

- [ ] 6.1 Run `./gradlew test koverVerify` and fix any coverage rule that fails
- [ ] 6.2 In a scratch Node project (npm workspaces, then pnpm through `npx pnpm`), declare `node`, approve it, and check with the built CLI that `shogun new` clones `node_modules` when the base is installed, installs when the lockfile changed, reports `needed` when unapproved, and that `npm test` runs in the new worktree

## Workflow follow-up

- Update `docs/roadmap.md` and `docs/roadmap.es.md` when archiving: a Node line under Platforms and a new "Web stacks" section with the follow-ups (Python virtual environments, a database per worktree, the package managers' cache sizes, independent Node projects in subfolders, cloning on Linux).
- Archive with `/opsx:archive` before opening the pull request, with `--base develop` and `--assignee CiroFeliu`.
