# Tasks

## 1. Declaration and templates

- [ ] 1.1 Add `env: JsonObject? = null` to `MultirepoDeclaration` and a pure parser of shared variables per alias (`TemplatePart.RepositoryPort`, shared-template rules: `${worktree}`, `$$`, `${port:<alias>.<key>}`; rejects `${port}`, `${port:<key>}`, unknown aliases, non-object entries, non-string values, invalid and reserved names, hidden characters); verified by unit tests covering every scenario of "Declare shared variables per repository" and "Shared variable templates".
- [ ] 1.2 Call the parser from `ResolveMultirepoUseCase.validate`, naming the alias, variable and file; verified by `ResolveMultirepoUseCaseTest` cases for an unknown alias and a reserved name.
- [ ] 1.3 Add the shared variables to `Multirepo.approvalSubject` only when present; verified by tests that a multirepo without `env` keeps its subject, and that changing or adding a shared variable changes it.
- [ ] 1.4 Check that ShogunAi's own writes keep `env` (`ConfigureMultirepoUseCase` subcommands and the JSON source round trip); verified by a `ConfigureMultirepoUseCaseTest` case that adds a repository to a declaration with `env` and keeps it approved and unchanged.

## 2. Resolution

- [ ] 2.1 Add the `SharedEnvironmentSource` port with `NONE`, `UnresolvedVariable` with its typed reasons, and `WorktreeEnvironment.unresolved`; verified by compilation of `./gradlew :core:compileKotlin` with no warnings.
- [ ] 2.2 Implement `MultirepoSharedEnvironment` (approved multirepo declaring the repository, the alias's shared variables, partner worktree on the same branch else the main checkout, invalid `env` reported as a reason); verified by tests with `MultirepoTestWorld` for partner, loose worktree, main checkout, partner joining later and unapproved multirepo.
- [ ] 2.3 Extend `ResolveWorktreeEnvironmentUseCase`: resolve each referenced worktree without shared variables and with the same `reserve`, render shared variables, merge providers → shared → declared, report unresolved ones; verified by `ResolveWorktreeEnvironmentUseCaseTest` cases for every scenario of "Referenced ports are the referenced worktree's own", "Shared variables are delivered with the worktree's environment" and "Report shared variables that can't be resolved".

## 3. Command line and desktop app

- [ ] 3.1 Wire `MultirepoSharedEnvironment` into `ShogunCli` and `AppContainer`; verified by a CLI test running `shogun env` in an `app` worktree of a test multirepo and getting `API_URL` with the partner's port.
- [ ] 3.2 Print unresolved shared variables in `shogun env` (standard error, exit 0) and in the `SessionStart` hook's context; verified by CLI tests for the key-not-declared, unapproved-repository and invalid-declaration cases and for delivery to `$CLAUDE_ENV_FILE`.
- [ ] 3.3 Show shared variables in `shogun multirepo trust` and `shogun multirepo show` (text and `--json` with an `env` object); verified by CLI tests for both commands.
- [ ] 3.4 Show shared variables in the desktop app's multirepo review (`MultirepoReviewDetails`, English and Spanish strings); verified by a desktop UI test that finds `API_URL` and its template in the review.

## 4. Documentation and validation

- [ ] 4.1 Document shared variables in `docs/guide/multirepo.md`, `docs/guide/configuration.md` and their `.es.md` translations (syntax, which worktree a reference uses, precedence, approval, when the value is refreshed, older versions dropping `env` on rewrite); verified by `mkdocs build --strict`.
- [ ] 4.2 Run `./gradlew test` and check it passes with no warnings.
- [ ] 4.3 Validate end to end with two scratch repositories in a scratch multirepo: `shogun env` in the `app` worktree prints `API_URL` with the port that `shogun env` prints as `SERVER_PORT` in `api`'s worktree on the same branch, and the main checkout's port for a loose worktree.

## Workflow follow-up

- Implementation commit after validation.
- `/opsx:archive multirepo-shared-variables`, removing the roadmap item from `docs/roadmap.md` and `docs/roadmap.es.md`.
- Check whether `claude-session-names` merged into `develop`, bring `develop` in, re-run the tests, and open the pull request against `develop`.
