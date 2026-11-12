# Proposal

## Why

All 88 specs pass `openspec validate --specs`, but `--strict` reports 71 warnings "Requirement text is very long (>500 characters)" across 30 specs. Long requirement bodies mix the rule with its examples and edge cases, which makes them hard to review and lets later changes keep growing them. Nothing in CI checks the specs, so the warnings can come back as soon as they are fixed.

## What Changes

- Reword the 71 requirements whose text is longer than 500 characters so each one is under the limit: state the rule in the requirement body and move examples, lists and edge cases into scenarios, splitting a requirement only where it states several independent behaviors.
- Keep exactly what each requirement requires: no behavior changes, no new or dropped obligations, and every requirement keeps at least one scenario.
- Run `openspec validate --specs --strict` in the Docs workflow (`.github/workflows/docs.yml`), with `openspec/**` added to its paths filter and the OpenSpec CLI installed at the version used locally, so a pull request that brings back a warning fails.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

Rewording only, behavior unchanged: `agent-workflow-guide`, `claude-code-hooks`, `claude-session-names`, `command-line-tool`, `desktop-turn`, `emulator-provider`, `herdr-integration`, `installation-check`, `ios-simulator-provider`, `machine-status-bar`, `multirepo-desktop`, `multirepo-groups`, `multirepo-trust`, `project-configuration`, `repository-provisioning`, `repository-trust`, `resource-pools`, `stack-detection`, `test-coverage`, `visual-theme`, `worktree-auto-refresh`, `worktree-base-branch`, `worktree-detail`, `worktree-environment`, `worktree-overview`, `worktree-row`, `worktree-status`, `worktree-terminal-launch`, `worktree-views`, `worktree-workspace`.

New behavior:

- `continuous-integration`: the specs are validated in strict mode on every pull request that touches `openspec/`.

## Impact

- `openspec/specs/*/spec.md` of the 30 capabilities above, through this change's deltas.
- `.github/workflows/docs.yml`: a new step and a wider paths filter. The workflow is created by the docs-ci change (PR #294), so this change builds on it.
- No Kotlin code, tests or coverage rules change.
