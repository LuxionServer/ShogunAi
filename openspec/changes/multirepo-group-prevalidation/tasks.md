## 1. Core

- [ ] 1.1 Write failing regression tests in `CreateGroupUseCaseTest`: a group whose second repository has a missing local file creates no worktree or branch in the first and reports the second as `FAILED` with `LocalFilesUnavailable` and the first as `SKIPPED` blocked by it; several repositories with unavailable files; a repository already on the branch stays `EXISTING` and isn't checked; no fetch runs when the check stops the group; a repository whose check throws is left to its creation
- [ ] 1.2 Add `GroupRepositoryOutcome.SKIPPED` and `GroupRepositoryResult.blockedBy`
- [ ] 1.3 Split `CreateGroupUseCase` into a planning phase (listing, local files check) and a creation phase, calling `onRepository` once per member in order

## 2. CLI

- [ ] 2.1 Test and print the skipped repository's line on standard error in `shogun new` for a group
- [ ] 2.2 Test and print `skipped` with its error in `groupCreationJson`

## 3. Desktop

- [ ] 3.1 Add `groupRepositorySkipped(aliases)` to `Strings`, `EnglishStrings` and `SpanishStrings`, and use it in the "New change" dialog's result label
- [ ] 3.2 Skip opening in a runner when the creation left no worktree to open, with a `WorktreeBoardTest` test

## 4. Validation and docs

- [ ] 4.1 Run `./gradlew test koverVerify` and keep `core` and `cli` at 100%
- [ ] 4.2 Remove the first bullet of roadmap item 6 in `docs/roadmap.md` and `docs/roadmap.es.md` when archiving
