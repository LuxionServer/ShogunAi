# Tasks

## 1. Core

- [x] 1.1 Add `DeleteMergedBranchUseCase` and verify with tests for a deleted branch, a branch Git refuses to delete, and the command it runs
- [x] 1.2 Document it in `docs/architecture/domain` (English and Spanish) and verify with `mkdocs build --strict`

## 2. Hook commands

- [x] 2.1 Parse `hook worktree-create` / `hook worktree-remove` and add `readInput` to `ShogunCli`, and verify with parser tests (missing or unknown hook name)
- [x] 2.2 Implement `worktree-create` and verify with tests for a project, no project (`.claude/worktrees`), `cwd` in a subfolder, resuming an existing worktree, invalid JSON or missing `name` (exit 2), and a failed creation (exit 1, empty stdout)
- [x] 2.3 Implement `worktree-remove` and verify with tests for a merged branch, a kept branch, and a path that isn't a linked worktree
- [x] 2.4 Document the hook commands in `docs/architecture/cli` (English and Spanish) and verify with `mkdocs build --strict`

## 3. Recipes

- [x] 3.1 Write `docs/agents.md` and `docs/agents.es.md` (Claude Code hooks, Cursor, Codex marked unverified, generic `shogun new`), add them to `mkdocs.yml`, link them from the README in both languages, update the roadmap, and verify with `mkdocs build --strict`

## 4. Integration checks

- [x] 4.1 Run `./gradlew test` and verify every test passes
- [x] 4.2 With the packaged launcher on `PATH` as `shogun` and the two hooks passed through `claude --settings`, run `claude -p -w <name>` in a throwaway repository with `.shogunai.json`, and verify the worktree is created by the hook on `feature/<name>` with the provisioned files; then run the remove hook on it and verify the worktree and merged branch are gone
- [x] 4.3 Verify the Cursor recipe's command (`shogun provision` run inside a fresh `git worktree add` worktree) copies the files
- [x] 4.4 Run `openspec validate agent-worktree-hooks --strict` and verify it passes

## Workflow follow-up

- Archive the change (`/opsx:archive`) in the same branch before opening the pull request.
