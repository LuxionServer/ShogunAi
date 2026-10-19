# Tasks

## 1. Shared rules

- [x] 1.1 Add the `--json` flag parsing for the listed commands and the shared JSON output helper, rejecting `--json` elsewhere; add parsing tests (accepted position, unknown on a hook) and verify they pass
- [x] 1.2 Make `--json` select non-interactive approval for `new` and `provision` and report-only `trust`; add tests that no question is printed and verify they pass

## 2. Commands

- [x] 2.1 Add JSON for `new`, `provision` and `provision --check`, document their fields in `docs/guide/cli.md` and `cli.es.md`; add CLI tests for each spec scenario and verify they pass
- [x] 2.2 Add JSON for `trust` and `clean` (dry run and `--yes`), with docs in both languages; add CLI tests for each spec scenario and verify they pass
- [x] 2.3 Add JSON for `gradle`, `gradle stop`, `xcode` and `xcode clean`, with docs in both languages; add CLI tests with fake providers and verify they pass
- [x] 2.4 Add JSON for `emulator` and `simulator` start, stop and status, with docs in both languages; add CLI tests and verify they pass
- [x] 2.5 Add JSON for `remove` and `repair`, with docs in both languages; add CLI tests and verify they pass
- [x] 2.6 Add a test per command asserting every documented field is present (with `null` where it doesn't apply) and verify they pass

## 3. Validation

- [x] 3.1 Update the usage text and per-command help, run `./gradlew test` and `mkdocs build --strict`, and verify both pass
- [x] 3.2 In a scratch repository, pipe `shogun new --json`, `shogun provision --check --json` and `shogun clean --json` into `jq` and verify each parses and matches the docs

## Workflow follow-up

- Implement after `cli-desktop-parity` is merged (it adds `remove` and `repair`); archive before opening the pull request.
