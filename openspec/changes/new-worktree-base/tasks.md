# Tasks

## 1. Core

- [ ] 1.1 Add `WorktreeError.StartPointNotFound(ref)` and its translations in `worktreeError` (English and Spanish); verify `StringsTest` covers it with its data kept.
- [ ] 1.2 Add `ResolveStartPointUseCase` (`git rev-parse --verify --quiet <ref>^{commit}` in the given directory); verify with `ResolveStartPointUseCaseTest`: a ref resolves to its commit in that directory, an unknown ref fails with `StartPointNotFound`.
- [ ] 1.3 Add the optional `startPoint` to `CreateWorktreeUseCase`, appended after `-b <branch>`; verify with `CreateWorktreeUseCaseTest` that the command includes it when given and is unchanged without it.

## 2. CLI

- [ ] 2.1 Parse `--from <ref>` in `parseNew`, rejecting it with `--branch` and without a value; verify with `ShogunCliTest` usage cases (exit code 2).
- [ ] 2.2 Resolve the start point in `new` (`--from` or the working directory's `HEAD`, left out when `HEAD` has no commit) and pass it to the use case; verify with `ShogunCliTest`: from a linked worktree the branch starts at that worktree's HEAD, `--from` starts at the ref, an unknown `--from` creates nothing and exits with code 1.
- [ ] 2.3 Resolve the hook's start point from the input's `cwd`; verify with `HookCommandsTest` that a session in a linked worktree branches from its HEAD.
- [ ] 2.4 Update the help text; verify the help test.

## 3. Docs and validation

- [ ] 3.1 Update `docs/architecture/cli.md`, `domain.md` and their `.es.md` versions (start point, `--from`, the hook); verify `mkdocs build --strict` succeeds.
- [ ] 3.2 Run `./gradlew test`, then check by hand with a temporary repository: `shogun new` from a linked worktree on another branch, `--from`, an unknown `--from`, and the hook with a `cwd` in a linked worktree.

## Workflow follow-up

- Archive the change (`/opsx:archive new-worktree-base`) once validated, in the same branch, before opening its pull request against `develop`.
- After it ships, replace the two-command epic workaround in the global agent notes with `shogun new <id>` from the epic worktree (or `--from`).
