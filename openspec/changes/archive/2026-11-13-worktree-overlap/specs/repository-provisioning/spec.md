## ADDED Requirements

### Requirement: Declare files whose overlaps are expected
`.shogunai.json` MAY declare `overlap.ignore`, a list of Git glob pathspecs relative to the repository root naming files that several worktrees are expected to change at once, such as a roadmap every change edits line by line. An `overlap` field that isn't an object, or an `ignore` that isn't a list of strings, SHALL make the file invalid with an error naming it, like any other wrong field. Writing `.shogunai.json` from the settings SHALL keep the field unchanged.

#### Scenario: Declared patterns
- **WHEN** `.shogunai.json` contains `{ "overlap": { "ignore": ["docs/roadmap*.md", "openspec/specs/**"] } }`
- **THEN** the repository's expected overlaps are `docs/roadmap*.md` and `openspec/specs/**`

#### Scenario: Not declared
- **WHEN** `.shogunai.json` has no `overlap` field, or the repository has no `.shogunai.json`
- **THEN** the repository declares no expected overlaps

#### Scenario: Wrong type
- **WHEN** `.shogunai.json` has `"overlap": { "ignore": "docs/roadmap.md" }`
- **THEN** reading the file fails with an error that names `.shogunai.json` and says `overlap.ignore` must be a list of strings

#### Scenario: Kept when the settings write the file
- **WHEN** the user turns on Gradle in the settings of a repository whose `.shogunai.json` declares `overlap.ignore`, and saves
- **THEN** `overlap.ignore` is unchanged
