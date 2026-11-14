# node-provider Specification

## Purpose
Defines the Node provider: an opt-in provider that gives every new or provisioned worktree of a Node repository its dependencies, cloned from the base repository when they match or installed with the repository's package manager once the repository is approved, and tells agents how to keep them current.

## Requirements

### Requirement: Opt-in Node provider
Everything the Node provider does SHALL apply only to repositories whose `.shogunai.json` declares `"providers": { "node": { … } }`. For any other repository the system SHALL NOT clone `node_modules`, run a package manager, report anything about Node dependencies, nor add Node rules to the agent context.

#### Scenario: Repository without the provider
- **WHEN** a repository with `package.json` and `package-lock.json` doesn't declare the provider and a worktree is created
- **THEN** no `node_modules` is cloned, no package manager runs and the result says nothing about Node dependencies

### Requirement: Find the package manager from the lockfile
The package manager SHALL be found from the lockfiles in the repository's main folder: `package-lock.json` or `npm-shrinkwrap.json` for npm, `pnpm-lock.yaml` for pnpm, `yarn.lock` for Yarn, `bun.lock` or `bun.lockb` for bun. When several are present, the manager named by `packageManager` in `package.json` SHALL win if one of its lockfiles exists, and otherwise the first of pnpm, Yarn, bun and npm. Lockfiles in subfolders SHALL be ignored.

#### Scenario: pnpm repository
- **WHEN** the main folder has `package.json` and `pnpm-lock.yaml`
- **THEN** the manager is pnpm

#### Scenario: Several lockfiles with packageManager
- **WHEN** the main folder has `yarn.lock` and `package-lock.json`, and `package.json` declares `"packageManager": "npm@10.9.0"`
- **THEN** the manager is npm

#### Scenario: Several lockfiles without packageManager
- **WHEN** the main folder has `yarn.lock` and `package-lock.json` and `package.json` declares no `packageManager`
- **THEN** the manager is Yarn

#### Scenario: No lockfile
- **WHEN** the main folder has `package.json` and no lockfile
- **THEN** no manager is found, nothing is cloned or installed, and the result says the dependencies are needed because there is no lockfile

### Requirement: Prepare dependencies when a worktree is prepared
For a repository that declares the provider, creating a worktree in every creation flow and provisioning an existing worktree SHALL prepare its dependencies when the worktree's main folder has no `node_modules`. It SHALL run after the files are copied and the pods are prepared, and before the post-creation commands. A worktree that already has `node_modules` SHALL be left unchanged.

#### Scenario: Post-creation command after the dependencies
- **WHEN** the repository also declares `npm run build` as an approved post-creation command
- **THEN** it runs after the dependencies are cloned or installed

#### Scenario: Already present
- **WHEN** an existing worktree that has `node_modules` is provisioned
- **THEN** nothing is cloned or installed and the result says the dependencies are present

### Requirement: Clone matching dependencies from the base repository
On macOS, the system SHALL clone with copy-on-write every `node_modules` folder of the base repository that Git ignores into the same place of the worktree when the base's lockfile equals the worktree's and the base's install marker is not older than its lockfile. Cloning SHALL never fall back to a full copy.

#### Scenario: Lock matches
- **WHEN** the worktree's `package-lock.json` equals the base's and the base's `node_modules/.package-lock.json` is newer than it
- **THEN** the worktree gets `node_modules` cloned from the base, no package manager runs, and the result reports the dependencies as cloned

#### Scenario: Workspaces
- **WHEN** the base has `node_modules`, `packages/api/node_modules` and `packages/web/node_modules` from a pnpm workspace and its lock matches
- **THEN** the three folders are cloned into the worktree

#### Scenario: Branch changed the lockfile
- **WHEN** the worktree's lockfile differs from the base's
- **THEN** nothing is cloned and the dependencies are installed

#### Scenario: Base not installed since a pull
- **WHEN** the base's lockfile is newer than its install marker
- **THEN** nothing is cloned and the dependencies are installed

#### Scenario: Manager without a marker
- **WHEN** the manager is bun, or Yarn with Plug'n'Play and no `node_modules`
- **THEN** nothing is cloned and the dependencies are installed

#### Scenario: Clone fails
- **WHEN** the base is on a volume without copy-on-write and a clone fails
- **THEN** every `node_modules` cloned so far is removed from the worktree and the dependencies are installed

#### Scenario: Other operating system
- **WHEN** ShogunAi runs on Linux or Windows
- **THEN** nothing is cloned and the dependencies are installed

### Requirement: Install markers of each package manager
The install marker SHALL be `node_modules/.package-lock.json` for npm, `node_modules/.modules.yaml` for pnpm, and `node_modules/.yarn-integrity` or `node_modules/.yarn-state.yml` for Yarn. bun SHALL have no marker.

#### Scenario: Yarn classic marker
- **WHEN** the manager is Yarn, the locks match and the base's `node_modules/.yarn-integrity` is newer than its `yarn.lock`
- **THEN** `node_modules` is cloned

#### Scenario: No marker in the base
- **WHEN** the base's `node_modules` exists but has no marker for the manager
- **THEN** nothing is cloned and the dependencies are installed

### Requirement: Install dependencies only when the repository is approved
When the dependencies aren't cloned, the system SHALL install them in the worktree's main folder with the manager and a frozen lockfile: `npm ci`, `pnpm install --frozen-lockfile`, `yarn install --immutable` when the main folder has `.yarnrc.yml` and `yarn install --frozen-lockfile` otherwise, or `bun install --frozen-lockfile`. It SHALL install only when the repository is approved; otherwise it SHALL report the dependencies as needed because the repository isn't approved.

#### Scenario: Approved repository
- **WHEN** an approved repository with `pnpm-lock.yaml` creates a worktree whose dependencies can't be cloned
- **THEN** `pnpm install --frozen-lockfile` runs in the worktree and the result reports the dependencies as installed with pnpm

#### Scenario: Yarn Berry
- **WHEN** the repository has `yarn.lock` and `.yarnrc.yml`
- **THEN** `yarn install --immutable` runs

#### Scenario: Repository not approved
- **WHEN** the repository isn't approved and the worktree is created by `shogun hook worktree-create`
- **THEN** no package manager runs, and the result says the dependencies are needed because the repository isn't approved and names the install command

#### Scenario: Clone needs no approval
- **WHEN** the repository isn't approved and the base's install matches the worktree's lockfile on macOS
- **THEN** `node_modules` is cloned

### Requirement: Dependencies never fail the worktree
When the install command can't be found, exits with an error or fails in any other way, the worktree SHALL be kept and the result SHALL report the dependencies as needed, with the reason: the command wasn't found, or its exit code and the last lines of its error output. Installing SHALL stop after 30 minutes, like `flutter pub get`, and be cancellable like a post-creation command.

#### Scenario: Manager not installed
- **WHEN** the repository uses pnpm, `pnpm` can't be found and a worktree is created
- **THEN** the worktree is created and the result says the dependencies are needed because `pnpm` wasn't found

#### Scenario: Install fails
- **WHEN** `npm ci` exits with code 1 because `package.json` and the lockfile are out of sync
- **THEN** the worktree is created, the post-creation commands still run, and the result says the dependencies are needed with the exit code and the error lines

#### Scenario: Cancelled
- **WHEN** the user cancels the creation while `npm ci` runs
- **THEN** the command is stopped like a post-creation command

### Requirement: Dependencies in the command results
`shogun new`, `shogun provision` and the worktree creation hook SHALL report the outcome when the provider is declared: in text as one line, and in JSON as `node.dependencies` with `status` `cloned`, `installed`, `present` or `needed`, the `manager` when one was found, and, when needed, the `reason` and the install command.

#### Scenario: JSON output
- **WHEN** `shogun provision --json` runs in a worktree of an approved npm repository and `npm ci` succeeds
- **THEN** the JSON has `node.dependencies.status` `installed` and `manager` `npm`

#### Scenario: Needed in text
- **WHEN** `shogun new TASK-123` creates a worktree of an unapproved repository whose dependencies can't be cloned
- **THEN** the output has a line saying the dependencies are needed, why, and the command to run, such as `npm ci`

#### Scenario: Provider not declared
- **WHEN** the repository doesn't declare the provider
- **THEN** the JSON has no `node` field and the text has no line about it

### Requirement: Tell the agent how to keep dependencies current
The `SessionStart` context and `shogun guide resources` for a repository that declares the provider SHALL say that the worktree has its own `node_modules`, to run the manager's install after `package.json` or the lockfile change, naming the repository's manager, never to link or copy `node_modules` from another worktree, and to take dev server ports from variables declared with `${port}`.

#### Scenario: pnpm repository
- **WHEN** a session starts in a worktree of a repository that declares `node` and has `pnpm-lock.yaml`
- **THEN** the context says to run `pnpm install` after `package.json` or `pnpm-lock.yaml` change

#### Scenario: Guide
- **WHEN** the user runs `shogun guide resources` in a worktree of a repository that declares the provider
- **THEN** the output includes the same Node rule

### Requirement: Node tools in the installation check
For each registered project whose repository declares `node`, the installation check SHALL report a check for the package manager found from its lockfile and, unless it is bun, a check for `node`. A missing tool SHALL be a warning with the fix to install it. A repository without a lockfile SHALL get only the `node` check.

#### Scenario: pnpm project
- **WHEN** a project declares `node`, has `pnpm-lock.yaml`, and `node` and `pnpm` are on the `PATH`
- **THEN** `shogun doctor` reports passing checks for `node` and `pnpm`, naming where each was found

#### Scenario: Manager missing
- **WHEN** a project declares `node`, has `yarn.lock` and `yarn` isn't found
- **THEN** the Yarn check warns, with the fix to install Yarn or enable it with Corepack

#### Scenario: bun project
- **WHEN** a project declares `node` and has `bun.lock`
- **THEN** only the bun check is reported
