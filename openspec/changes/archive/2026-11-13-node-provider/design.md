# Design

## Context

What a Node repository looks like to ShogunAi, and what a fresh worktree lacks.

- **Layout.** `package.json` and one lockfile at the root: `package-lock.json` (npm, or `npm-shrinkwrap.json`), `pnpm-lock.yaml`, `yarn.lock` (Yarn classic, or Berry when `.yarnrc.yml` exists) or `bun.lock` (`bun.lockb` before bun 1.2). `package.json` may name its manager in `packageManager` (`"pnpm@9.12.0"`), which Corepack honours. Workspaces (npm, Yarn and bun `workspaces` in `package.json`, `pnpm-workspace.yaml`) keep a single lockfile at the root.
- **Ignored and generated.** `node_modules/` at the root and, with workspaces, in packages (pnpm always links each package's dependencies into its own `node_modules`; npm and Yarn do so when versions conflict). Yarn Berry with Plug'n'Play has no `node_modules` at all: `.pnp.cjs` and `.yarn/cache`.
- **Install markers.** Each manager leaves a file in `node_modules` when an install finishes: npm's hidden lockfile `node_modules/.package-lock.json`, pnpm's `node_modules/.modules.yaml`, Yarn classic's `node_modules/.yarn-integrity`, Yarn Berry's `node_modules/.yarn-state.yml` (node-modules linker). bun documents none.
- **A fresh worktree doesn't run.** Every script, test runner and dev server fails with "Cannot find module" until the dependencies are installed.
- **Installing runs code.** `npm ci`, `pnpm install`, `yarn install` and `bun install` run the root package's `preinstall`, `install`, `postinstall` and `prepare` scripts and, except where a manager blocks them by default, the dependencies' build scripts. This is the difference from `flutter pub get`, which ShogunAi runs without the repository's approval because it runs no repository code.
- **Machine.** Node 26 and npm 11 are installed where this was planned; pnpm, Yarn and bun aren't.

## Goals / Non-Goals

**Goals:**

- A fresh worktree of a Node repository runs its scripts with no manual step, in seconds when the base repository is installed.
- Never run repository code the user hasn't approved.
- npm, pnpm, Yarn and bun from the first version, including workspaces under one root lockfile.

**Non-Goals:**

- Independent Node projects in subfolders without a root workspace (a `web/` next to an `api/`, each with its own lockfile). They keep declaring their installs as post-creation commands. Roadmap follow-up.
- Ports for dev servers. They already work with `env` and `${port}` (`"PORT": "${port}"`); the guide and the agent rule say so.
- Showing or cleaning the managers' caches (npm's `~/.npm`, pnpm's store, Yarn's cache, bun's cache). Roadmap follow-up, next to the other shared caches.
- Installing Node or a manager, or switching versions (`.nvmrc`, `engines`, Volta). A missing or wrong version shows as a failed install.
- Cloning on Linux (`cp --reflink`) and Windows. They always install. Follow-up if it matters.
- Python virtual environments and a database per worktree, the other web follow-ups.

## Decisions

**Clone when the base matches, install otherwise.** Ciro chose this. The provider first tries what CocoaPods provisioning does: on macOS, when the worktree's lockfile has the same content as the base's and the base's install marker is not older than the base's lockfile, every `node_modules` of the base is cloned with `cp -c -R`, which runs nothing and shares blocks until they change. Otherwise it installs with the manager and a frozen lockfile. The marker's age is the guard against a base that pulled a new lockfile without installing: Git rewrites the lockfile, so it becomes newer than the marker. Alternatives: always install, which is slower and always needs an approval, or always clone, which can't follow a branch that changed its dependencies.

**Which `node_modules` folders are cloned.** The ignored folders named `node_modules` that `git ls-files -z --others --ignored --exclude-standard --directory` lists in the base, which stops at each ignored folder and so never descends into one. That covers the root and every workspace package. If one clone fails, the clones already made are removed, so the worktree never mixes cloned and installed folders, and the provider installs instead.

**Installing needs the repository's approval.** Ciro chose this. The approval subject gains `nodeInstall`, true when the repository declares the provider, so a repository with only the provider declared needs an approval, the dialogs and `shogun trust` show a line saying Node dependencies will be installed, and a pull that adds the provider invalidates an earlier approval. The digest adds `"nodeInstall": true` to the canonical form only when it is true, so every existing approval stays valid. The install runs inside the environment preparation, which already knows whether the repository is approved (`WorktreeEnvironment.declarationApproved`) after the creation flow settled the approval, and before the post-creation commands. When it isn't approved, the result says the dependencies are needed, naming the command, so the user can run it or `shogun trust`. Alternatives: installing without approval, like Flutter, rejected because install scripts are repository code; `--ignore-scripts` without approval, rejected because packages with native builds or generated clients (`postinstall`) would be left half installed.

**Frozen installs.** `npm ci`, `pnpm install --frozen-lockfile`, `yarn install --immutable` when `.yarnrc.yml` exists (Berry) and `yarn install --frozen-lockfile` otherwise (classic), `bun install --frozen-lockfile`. A worktree must not rewrite the lockfile it checked out; when `package.json` and the lockfile disagree, the failure is reported with the manager's own message. No lockfile at all is reported as needed, without running `npm install`, which would write one into the worktree.

**Package manager from the lockfile.** Only the main folder's lockfiles count. With several, `packageManager` decides (its name before `@`) when one of that manager's lockfiles exists; otherwise the order is pnpm, Yarn, bun, npm, since a leftover `package-lock.json` next to another manager's lock is the common mistake. The executables are taken from `PATH` (Corepack shims included), like `flutter`.

**Already present.** A worktree that has `node_modules` in its main folder is left as is, also when provisioned again, and reported as `present`. Reinstalling after the branch changes its dependencies is the agent's job, which the session rule tells it.

**One step next to Flutter.** A `NodeDependenciesPreparer` port with a `PrepareNodeDependenciesUseCase`, called by `PrepareWorktreeEnvironmentUseCase` after pods and Flutter, with the approval passed in. The outcome is `PreparedWorktree.node`, then `Worktree.node` and `ProvisionResult.node`, a `NodeDependencies` sealed model: `Cloned(manager, folders)`, `Installed(manager)`, `Present`, `Needed(manager?, reason, command?)` with reasons `NoLockfile`, `NotApproved`, `NotFound(executable)`, `Exited(code, lines)`, `Unexpected(message)`. Failures never roll the worktree back, like pods and Flutter. The install uses the 30-minute install limit Flutter uses (`CommandTimeouts.INSTALL`) and is cancellable.

**Detection.** Node is detected when the main folder has `package.json`; its lockfiles join the evidence. Only file names are read, like the other root-only stacks. A `package.json` used only for tooling in a JVM repository also gets the suggestion; detection is only a hint with its evidence, never applied by itself.

**`shogun doctor`.** A check for `node` and one for the manager found from the lockfile, each with an install fix (Node's site for `node` and npm; Corepack for pnpm and Yarn; bun's site). bun runs without Node, so a bun repository is checked only for `bun`. A repository without a lockfile is checked only for `node`.

**Agent context.** A workflow rule shown for repositories that declare `node`, with the manager's name filled in: the worktree has its own `node_modules`; run `<manager> install` after `package.json` or `<lockfile>` change; never link or copy `node_modules` from another worktree; dev servers take their port from a variable declared with `${port}` instead of a fixed one.

**Desktop.** `SuggestedProvider.NODE` needs a label and a switch in "Repository configuration" (English and Spanish), `RepositoryConfigDraft` carries `node`, the approval lines show the Node install; the desktop app gets the preparer through the shared `ShogunEngine` composition, so no desktop wiring is needed.

**File length caps.** Everything new lives in new files (`NodeDependencies.kt`, `PrepareNodeDependenciesUseCase.kt`, the CLI's `NodeDependenciesText.kt`), but the files that list every provider need their Node lines next to Flutter's, and they were exactly at their caps: `CommandJson.kt` (its Node lines fit under develop's cap of 1028 after the merge), `RepositoryProvisioningFiles.kt` (460 → 465: reading, writing and the option list of `providers.node`, counted after merging develop's `overlap.ignore`), `Strings.kt` (957 → 959), `EnglishStrings.kt` (1131 → 1133) and `SpanishStrings.kt` (1129 → 1131), counted after merging develop (+2 each: the switch label and the approval line) and `ProjectConfigViewModel.kt` (820 → 823: the switch setter and the suggestion branch). Splitting those files is a refactor outside this change.

**Doctor tool ids per manager.** Instead of one "package manager" id, `ProviderToolId` gets `NODE`, `NPM`, `PNPM`, `YARN` and `BUN`, so each check is labelled with the tool it found and its fix names that tool.

**Coverage.** No new `@Serializable` class whose fields all have defaults is added, so `core`'s maximum of missed branches doesn't change.

## Risks / Trade-offs

- [A cloned `node_modules` holds a native addon or a generated file built for the base's checkout] → It is cloned only when the lockfiles match, which pins every package version; files generated by the repository's own scripts (a Prisma client from a schema the branch changed) can still differ. The guide says to rerun the generator, and the agent rule says to reinstall after dependency changes.
- [Absolute paths inside `node_modules`] → npm, pnpm and Yarn write relative links in `node_modules` (`.bin`, pnpm's `.pnpm` links); pnpm's `.modules.yaml` names the global store, which is shared anyway. A package that writes the base's absolute path at install time keeps pointing into the base; accepted and documented.
- [The marker's age is a heuristic] → A base whose `node_modules` was edited by hand after an install could still be cloned. The comparison errs towards installing when in doubt (missing marker, missing lockfile, different content).
- [The desktop app started from Finder doesn't see the shell's `PATH`] → The executor already adds Homebrew's folders; managers installed elsewhere (nvm, Volta) are reported as not found from the app and work from the CLI and the hook. Same follow-up as Flutter's.
- [`git ls-files --directory` on a huge base] → It doesn't descend into ignored folders, so its cost is that of listing the tracked tree once, which creation already does for local files.
- [pnpm, Yarn and bun aren't installed on the planning machine] → Unit tests cover every manager's commands with a fake executor, and the end-to-end check runs npm for real and pnpm through `npx pnpm`.

## Migration Plan

Nothing to migrate. Repositories that don't declare `node` behave as before and keep their approvals.
