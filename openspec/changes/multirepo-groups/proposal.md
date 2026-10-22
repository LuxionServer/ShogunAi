# Proposal

## Why

A product often lives in several repositories (for example an app, its backend and its docs), side by side in one folder or scattered across the disk. A change that spans two of them today means creating a worktree in each by hand with matching branches, opening each one, and checking each one's state and cleanup separately, while nothing tells the agent of one repository where the others are. ShogunAi should let one change span several repositories while keeping one ordinary worktree and one agent per repository, coordinated by an agent that oversees them all, and it must adopt the projects and worktrees people already have without breaking anything that is open.

## What Changes

- A **multirepo** groups repositories. It's declared by a `.shogunai-multirepo.json` file in a folder of the user's choice that is never inside a repository: an alias per repository (relative or absolute path, so repositories needn't share a parent), a common worktrees folder (`worktreesRoot`), the `defaultRepos` a new change covers and the command of the multirepo's own agent. ShogunAi remembers the known multirepos in a new file of its own, `~/.shogunai/multirepos.json`. The declaration is written by `shogun multirepo` (`init`, `show`, `add-repo`, `remove-repo`, `defaults`, `agent`, `worktrees-root`, `delete`) and by the desktop app, never by hand. Adding a repository reuses its registered project unchanged, or registers one, and proposes `<worktreesRoot>/<alias>` as its project's worktrees folder, changed only if the user accepts, so the disk ends up organised like Herdr (`product-worktrees/app/TASK-12`, `product-worktrees/api/TASK-12`); deleting a multirepo leaves everything else as it was.
- A **group** is a branch with exactly the same name in two or more repositories of a multirepo, wherever each worktree lives and whoever created it, so worktrees that already exist with matching branches are grouped in place. Groups are computed from Git and the declaration only: nothing about them is written anywhere, and grouping never moves, renames or changes a worktree.
- `shogun new <id> [--repos a,b] [--fix] [--fetch]` creates or extends a group from the multirepo folder (with `defaultRepos`) or with `--repos` from any of its repositories: in each repository it does what `shogun new` does today, in that project's worktrees folder and with the usual name, starting at `origin/<base>`; it's idempotent, rolls back only the repository that fails, and opens nothing.
- `shogun open <id> --herdr` in the multirepo folder opens each worktree of the group exactly as a single worktree opens today, under its repository's space with its own environment and agent; `shogun open --herdr` there opens the multirepo agent in a Herdr workspace of its own.
- Every agent gets its context from ShogunAi rather than from other agents: the `SessionStart` hook in a group worktree adds the group, the other repositories with their worktree paths and branch, and where OpenSpec lives; in the multirepo folder it describes the multirepo and points to `shogun status`, which lists every group per repository (branch, changes, ahead/behind, merged, agents) and the ungrouped worktrees.
- `shogun clean` in the multirepo folder offers a group only when every worktree is clean and merged and no agent runs in any of them, and removes its worktrees with `--yes`, deleting the branches only with `--delete-branches`, as the rest of `clean`.
- Desktop app: the multirepo is a collapsible folder in the sidebar with its projects and summed activity; its view groups worktrees by change (header with the branch without its prefix, the full branch, repositories, combined state and actions to open all in Herdr, add a repository and clean up the group; an "Ungrouped" block; grouping by state as an alternative); rows elsewhere get a group tag that opens the group; a multirepo is added by dropping or choosing a folder, with detected repositories, existing projects and detected groups shown before confirming; it has a settings screen and a "New change" dialog.
- Without a multirepo, everything behaves exactly as today, and `projects.json` and `settings.json` keep their format.

## Capabilities

### New Capabilities
- `multirepo-groups`: declaring, configuring and deleting a multirepo from the command line, reusing registered projects, defining groups by branch, and creating, showing, opening, giving context to and cleaning up groups, plus the multirepo agent and the compatibility guarantees.
- `multirepo-desktop`: the multirepo in the desktop app: sidebar folder, multirepo view grouped by change, group tags, adding and configuring a multirepo and creating a change.

### Modified Capabilities
- `command-line-tool`: `shogun new`, `status`, `open`, `clean` and `multirepo` also work in a multirepo folder, which isn't a Git repository, and the `SessionStart` hook adds the group's context in a group worktree and the multirepo's context in the multirepo folder.
- `app-shell`: the sidebar lists a multirepo's projects inside its folder and sums their activity on it.
- `worktree-views`: a project's group header is preceded by its multirepo's name.
- `worktree-row`: the name cell shows the group tag of a worktree that belongs to a group.

## Impact

- `core`: model, reader and writer for `.shogunai-multirepo.json` and `~/.shogunai/multirepos.json`; discovery of the multirepo of a directory; group detection by branch; use cases to configure multirepos and to create, read, open and clean up groups, built on the existing creation, provisioning, status, agent detection, cleanup and Herdr use cases; a `HerdrClient` call to open a plain workspace.
- `cli`: `shogun multirepo …`, `new --repos`, group output of `status`, `open` and `clean`, the `SessionStart` context, help texts and JSON shapes.
- `desktopApp`: sidebar folder, multirepo view, group tags, add-multirepo dialog, multirepo settings and "New change" dialog, with strings in every supported language.
- Docs: a new `docs/guide/multirepo.md`, `docs/guide/cli.md` and `docs/guide/desktop-app.md`, with their Spanish translations; roadmap updated when archived.
- No new dependencies. Opening the app and registering projects from the command line (`shogun app`, `shogun project add`) are a separate change, `cli-app-projects`.
