# Tasks

## 1. Core

- [ ] 1.1 Extract `RepositoryConfigRules` from the readers (memory size, idle minutes, variable names, template placeholders, `envFile`) with typed errors, and make the readers use it; verify the existing `repository-provisioning` and `worktree-environment` reader tests still pass, and add `RepositoryConfigRulesTest` for each rule.
- [ ] 1.2 Add `RepositoryConfigWriter` that updates `providers.<known id>`, `env` and `envFile` on the parsed tree, keeps unknown fields and providers and key order, creates the file when absent and writes atomically; verify with `RepositoryConfigWriterTest` covering every "Write providers and environment" scenario.
- [ ] 1.3 Add the content-hash check before any `.shogunai.json` write (this writer and "move local files"); verify with tests for a file changed on disk and a file created meanwhile.

## 2. Settings state

- [ ] 2.1 Add the repository configuration state to `ProjectConfigViewModel` (switches, options, env entries, `envFile`, validation, unsaved-change tracking, unreadable-file state); verify with `ProjectConfigViewModelTest` cases for turning providers on and off, invalid values blocking save, and a malformed file disabling the section.
- [ ] 2.2 Implement the save order (validate, preview on first write, write the repository file, save the project, reload the board and the machine providers); verify with `ProjectConfigViewModelTest` cases: preview cancelled saves nothing, write failure keeps the project unsaved, success triggers the reloads.

## 3. UI

- [ ] 3.1 Build the "Repository configuration" section (shared-file note, provider rows with expandable options, environment list with add and remove, template help, `envFile` field) and the "this machine only" labels on the machine sections; verify in the running app on macOS and on one other platform (iOS simulator and Xcode hidden there).
- [ ] 3.2 Build the preview dialog showing the resulting file with the commit reminder; verify in the running app that confirming writes the file and cancelling keeps the edits.

## 4. Strings and docs

- [ ] 4.1 Add the new strings in both languages, including every validation message; verify `StringsTest` passes and a test asserts each documented template resolves.
- [ ] 4.2 Update `docs/architecture/ui.md` and `ui.es.md`, the repository configuration reference in the docs and the README section about `.shogunai.json` in both languages; verify `mkdocs build --strict` succeeds.
- [ ] 4.3 Run the app on the secondary display against a demo repository: turn on each provider with options, add environment variables, confirm the preview, and check the written file and the views (Machine section, worktree detail devices) against the scenarios; repeat with a malformed `.shogunai.json`.

## Workflow follow-up

- Archive the change (`/opsx:archive providers-configuration`) once validated, in the same branch, before opening its pull request against `epic/ui-redesign`.
- With the archive, remove `providers-configuration`'s line from the "Desktop app redesign" list in `docs/roadmap.md` and `docs/roadmap.es.md` (the last change of the epic removes the whole section).
