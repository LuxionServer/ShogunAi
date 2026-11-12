# Tasks

## 1. Reword the long requirements

- [x] 1.1 Write a `MODIFIED` delta for each of the 71 requirements over 500 characters in the 30 specs, keeping its header and every scenario, plus an `ADDED` requirement with its own scenarios for each behavior split out; verify with `openspec validate spec-requirement-length --strict`
- [x] 1.2 Check that no rule was lost: every sentence of the old bodies is either still in a body or moved, reworded, into a split requirement or a scenario; verify by listing the old sentences that aren't found verbatim after a simulated archive and reviewing each one
- [x] 1.3 Simulate the archive on a copy of `openspec/` and run `openspec validate --specs --strict` there; verify it reports 88 passed and no warning

## 2. Strict validation in CI

- [ ] 2.1 Bring `.github/workflows/docs.yml` into the branch: merge `origin/feature/docs-ci` while PR #294 is open, or `origin/develop` once it's merged; verify the file exists and the branch builds on #294's commits
- [ ] 2.2 Add `openspec/**` to the Docs workflow's paths filter, and a step that installs `@fission-ai/openspec@1.17.0` with npm and runs `openspec validate --specs --strict`; verify with `actionlint` or a YAML parse of the file
- [ ] 2.3 Check that the step fails on a warning: run the same command against the current `openspec/specs` (71 warnings) and confirm a non-zero exit code, then against the simulated archive and confirm exit code 0

## 3. Validation

- [ ] 3.1 Confirm no Kotlin source changed: `git diff --stat origin/develop...HEAD` lists only `openspec/` and `.github/workflows/docs.yml`
- [ ] 3.2 Open the pull request and confirm the Docs check runs and passes on it

## Workflow follow-up

- Archive the change (`/opsx:archive`), check `openspec validate --specs --strict` reports no warning, commit and push.
- If doctor-gradle-java merged first, rebase its `installation-check` text into this change's delta before archiving.
- Bring the branch up to date with `origin/develop` and open the pull request to `develop`, assigned to CiroFeliu, once #294 is merged.
