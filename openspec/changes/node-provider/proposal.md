# Proposal

## Why

Every ShogunAi provider today targets mobile and JVM stacks, while most people running agents in parallel work on web repositories, where a fresh worktree doesn't run either: it has no `node_modules`. A `node` provider is the first step of a new direction towards web and non-JVM backend stacks.

## What Changes

- New opt-in `node` provider, declared as `"providers": { "node": {} }` in `.shogunai.json`.
- The package manager is found from the lockfile in the repository's main folder: npm (`package-lock.json` or `npm-shrinkwrap.json`), pnpm (`pnpm-lock.yaml`), Yarn (`yarn.lock`) or bun (`bun.lock` or `bun.lockb`), with `packageManager` in `package.json` deciding when there are several.
- Every new or provisioned worktree without `node_modules` gets its dependencies:
  - cloned with copy-on-write from the base repository on macOS when the base's install matches the worktree's lockfile, which runs no code;
  - otherwise installed with the manager and a frozen lockfile (`npm ci`, `pnpm install --frozen-lockfile`, `yarn install --immutable` or `--frozen-lockfile`, `bun install --frozen-lockfile`), only when the repository is approved, because installing runs the repository's and its dependencies' scripts.
- Workspaces are covered by the single root lockfile: every `node_modules` folder of the base is cloned, or the one install at the root fills them.
- The repository's approval covers the Node install: the approval dialogs, `shogun trust` and its JSON say that dependencies will be installed, and declaring the provider makes a repository need an approval.
- `shogun new`, `shogun provision` and the worktree creation hook report the outcome as a text line and as `node.dependencies` in JSON.
- Stack detection finds Node from the main folder's `package.json`, and `shogun stacks` and the app suggest `node`.
- `shogun doctor` checks `node` and the detected package manager for projects that declare the provider.
- A `SessionStart` rule tells agents to reinstall with the repository's manager after the lockfile changes and to reserve dev server ports through `env`.
- The desktop app's "Repository configuration" offers a Node switch, and worktrees created from the app get the same preparation.
- The providers guide (English and Spanish) gets a Node section, and the roadmap gets a "Web stacks" section with the follow-ups and a Node line under Platforms.

## Capabilities

### New Capabilities

- `node-provider`: the opt-in Node provider: package manager detection, cloning or installing a worktree's dependencies, reporting the outcome, its tools in `shogun doctor` and the agent context.

### Modified Capabilities

- `stack-detection`: detect the Node stack and suggest `node`.
- `repository-provisioning`: read and write `providers.node` in `.shogunai.json`.
- `repository-trust`: the approval of a repository that declares `node` covers installing its dependencies, and shows it.
- `project-configuration`: a switch for the Node provider in "Repository configuration".
- `installation-check`: check `node` and the package manager for projects that declare `node`.

## Impact

- `core`: new model and use case for Node dependencies, `RepositoryProviders.node`, stack detection, approval subject and its digest, the worktree creation and provisioning flows, provider tool checks and the workflow rules.
- `cli`: `shogun new`, `shogun provision`, `shogun hook worktree-create`, `shogun stacks`, `shogun trust` and `shogun doctor` output.
- `desktopApp`: the Node switch, approval lines and strings (English and Spanish), and the environment preparer wired in `AppContainer`.
- Docs: `docs/guide/providers.md` and `.es.md`, `docs/roadmap.md` and `.es.md`.
- No new dependencies. Commands run as argv: `cp -c -R`, `npm`, `pnpm`, `yarn`, `bun`, `node`.
