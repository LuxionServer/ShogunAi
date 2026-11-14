# Proposal

## Why

When several agents work in parallel, each in its own worktree, their pull requests collide when two of them change the same file, and today someone has to work out by hand which files each change will touch.
ShogunAi already sees every worktree of a repository and the base each one's pull request will target (`shogun base`), so it can tell which files are being changed by more than one worktree at once; no runner does it for the others.

## What Changes

- A new `shogun overlap [<target>] [--all] [--json]` command lists the files that two or more active worktrees of the same repository change with respect to their own base: the branch's commits since it left its base, plus uncommitted and untracked changes.
  Run inside a linked worktree (or given a target) it shows the overlaps that involve that worktree; run in the main checkout, every overlap of the repository.
- Each worktree is compared with its own base (recorded base or main branch, as `shogun status` does); worktrees with different bases are still reported together, each one shown with its base.
- A repository can declare, in `.shogunai.json`, patterns of files whose overlaps are expected and not worth a warning (`"overlap": { "ignore": [...] }`, Git glob pathspecs), such as a roadmap every change edits line by line.
  Nothing is ignored by default; `--all` shows the declared ones too.
- The workflow guide gets a rule telling the agent to run `shogun overlap` before opening its pull request and to tell the person about any overlap.
- The control tower's signal for overlapping worktrees is left out of this first delivery and added to the roadmap.

## Capabilities

### New Capabilities

- `worktree-overlap`: detecting the files that several active worktrees of one repository change at once with respect to their bases, and reporting them from the command line.

### Modified Capabilities

- `repository-provisioning`: `.shogunai.json` gets the `overlap.ignore` field, validated like the other fields.
- `agent-workflow-guide`: the `worktrees` topic gets the rule to check `shogun overlap` before opening a pull request.

## Impact

- `core`: a new use case that reads each worktree's changed files against its base and groups them by file, a model for the result, and the `overlap.ignore` field in `RepositoryProvisioning` and its parser.
- `cli`: the `overlap` command (parsing, help, text and JSON output), in new files where possible, with minimal additions to `CliCommand.kt`, `ShogunCli.kt`, `CliHelp.kt`.
- `WorkflowRules`: one new rule.
- Docs: the CLI reference in `docs/` (en and es) and the roadmap (en and es) with the control tower follow-up.
- No new dependency; only `git` commands, run as argv.
