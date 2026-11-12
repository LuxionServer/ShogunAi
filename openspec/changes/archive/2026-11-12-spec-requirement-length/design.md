# Design

## Context

OpenSpec 1.17.0 measures a requirement's text as every non-blank line between `### Requirement:` and its first `####` header, outside code fences, and warns above 500 characters. Bullet lists in the body count. 71 requirements in 30 specs are over the limit, most of them between 500 and 700 characters. See proposal.md for why they are fixed now.

Other changes branched from `develop` are in flight. The doctor-gradle-java change has a MODIFIED delta for `installation-check`'s "Check the tools of each project's providers", one of the 71. The docs-ci change (PR #294, open) creates `.github/workflows/docs.yml` and adds a requirement at the end of `continuous-integration`.

## Goals / Non-Goals

**Goals:**

- `openspec validate --specs --strict` reports no warnings once this change is archived.
- The Docs workflow fails on any pull request that brings back a strict-mode warning in `openspec/`.

**Non-Goals:**

- Rewording requirements that are already under the limit, renaming requirements or reordering specs.
- Fixing other spec-quality issues the strict mode doesn't report.
- Kotlin code, tests or coverage.

## Decisions

### Deltas, not direct edits to the main specs

Each long requirement goes through a `MODIFIED` delta in this change and reaches `openspec/specs/` on archive, like any other change. The delta keeps the requirement's header, so a parallel change that modifies the same requirement still matches it by name.

Alternative: editing `openspec/specs/` directly with `skip_specs: true`. Rejected because the text that is normative does change wording, and the deltas let review compare old and new requirement by requirement.

### Move detail into scenarios first, split only when needed

For each requirement, in this order:

1. State the rule once in the body and move examples, enumerations and edge cases into scenarios: the existing ones when they already describe the case, new ones otherwise.
2. When the body states several independent behaviors (for example a command's output and its exit code, or two different triggers), keep the header and every existing scenario under `MODIFIED`, keep in its body the behavior the header names, and add each behavior removed as its own `ADDED` requirement with its own scenarios.

Every existing scenario is kept. A new scenario only restates something the old body already required, with a WHEN/THEN that reads it as a case. Moving text into a scenario must not weaken a SHALL into an example: a rule the old body stated stays in a body (this requirement's or a split one's), and only examples and the items of a list whose rule stays in the body (for example the computer-use tools that only look, or the platform's default SDK folders) move into scenarios, each item into one.

Alternative: always splitting. Rejected because `ADDED` requirements are appended at the end of the spec, far from their siblings, and each split multiplies headers that parallel changes could collide with.

### Strict validation in the Docs workflow

The Docs job gains a step that installs `@fission-ai/openspec@1.17.0` (the version used locally) with npm and runs `openspec validate --specs --strict`, and its paths filter gains `openspec/**`. The step goes in the existing job rather than a new workflow because it is the same kind of check (documentation that must build cleanly) and the branch protection already expects that check's name.

The version is pinned so a new OpenSpec release can't turn a green pull request red without a change in the repository; it is raised on purpose together with the local one.

This is a CI behavior, so `continuous-integration` gets one `ADDED` requirement for it, separate from docs-ci's requirement for the documentation site.

### Building on PR #294

`.github/workflows/docs.yml` only exists on `feature/docs-ci`. If #294 is still open when the workflow task starts, `origin/feature/docs-ci` is merged into this branch; once #294 is merged, bringing the branch up to date with `develop` resolves to the same content. The pull request for this change is opened only after #294 is merged, so its diff against `develop` doesn't include docs-ci.

## Risks / Trade-offs

- [A parallel change archives a `MODIFIED` copy of one of the 71 requirements with its old long text] → The new step makes that change's Docs check fail, which tells its author to reword it. For doctor-gradle-java, if it merges first, this change's delta for that requirement is rebased on its new text before archiving.
- [A rewording changes meaning by accident] → Review compares each `MODIFIED` block with the old requirement; tasks include a pass checking that every SHALL of the old text is still present somewhere in the new requirement or its split ones.
- [Both this change and docs-ci append to the end of `continuous-integration/spec.md`] → A textual conflict when bringing the branch up to date, resolved by keeping both requirements.
- [`npm install -g` adds time to the Docs job] → The package is small; the job's timeout stays at 10 minutes.
