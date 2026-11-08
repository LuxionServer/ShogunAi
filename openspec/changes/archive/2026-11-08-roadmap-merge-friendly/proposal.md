# Proposal

## Why

Every pull request that archives a change updates the roadmap, and today two of them almost always conflict, because Git merges by line. The "Platforms" cell of the Direction table and the Orca paragraph are each a single line hundreds of words long, and every archived change appends a sentence to one of them. Planned sections carry explicit numbers (`### 1.`, `### 2.`…), and `AGENTS.md` asks to renumber them when one is removed, so every removal touches every heading below it. Both problems are duplicated in `docs/roadmap.es.md`.

## What Changes

- `docs/roadmap.md` and `docs/roadmap.es.md` use semantic line breaks: one sentence per line. The rendered pages read the same.
- Direction replaces its table with one subsection per layer (Engine and CLI, Control tower, Platforms) and one for the runners (Claude Code and Herdr, Orca). Each has its status on a line of its own and its capabilities as short bullets, one per line, condensed from the run-on sentences without dropping any capability.
- Planned sections lose their numbers; the intro says their order is the priority. The "see point 2 above" reference becomes a link to the section.
- `AGENTS.md`'s rule for updating the roadmap when archiving drops "renumber the remaining ones" and states the merge-friendly rules: one sentence per line, no renumbering, remove only the completed bullet or section, add follow-ups as new lines in their section, and touch Direction only when a layer's status changes or a genuinely new capability needs its own bullet, never by extending an existing line.
- `docs/process/openspec.md` and `openspec.es.md` say how the roadmap is updated on archive, in the same terms.

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- Docs: `docs/roadmap.md`, `docs/roadmap.es.md`, `docs/process/openspec.md`, `docs/process/openspec.es.md`.
- `AGENTS.md`.
- No behavior changes, so no spec deltas.
- In-flight pull requests that touch the roadmap will conflict with this one once more; it's best merged when few of them are open.
