# Tasks

## 1. Default start point

- [ ] 1.1 Rewrite the default-start test in `ProjectWorktreesTest` for a project whose main branch is `main` and whose base repository is on `develop`, expecting `StartOption.Current` preselected and first, `origin/main` second, and `develop` recorded as the base; see it fail
- [ ] 1.2 Add a test that choosing `origin/main` creates the branch at its commit
- [ ] 1.3 Make `WorktreeBoard.effectiveStartFrom` default to `StartOption.Current` and order `startOptions` as current, main branch, others
- [ ] 1.4 Add the "same as shogun new" hint to `startFromCurrentBranch` in `EnglishStrings` and `SpanishStrings`, with a test for both labels

## 2. Docs

- [ ] 2.1 Update "New branch" in `docs/guide/desktop-app.md` and `docs/guide/desktop-app.es.md`
- [ ] 2.2 Update the "Start from" selector in `docs/architecture/ui.md` and `docs/architecture/ui.es.md`

## 3. Validation

- [ ] 3.1 Run `./gradlew test koverVerify`
- [ ] 3.2 Check the dialog in the app running headless (`shogun headless start`) with a sample git-flow repository: default, order and hint
