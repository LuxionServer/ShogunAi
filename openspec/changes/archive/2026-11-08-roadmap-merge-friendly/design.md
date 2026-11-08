# Design

## Context

See proposal.md. The roadmap is updated by almost every archived change, and several changes are usually in flight at once, each in its own worktree and pull request. Conflicts there are pure noise: two pull requests rarely edit the same item, they only edit the same line.

## Goals / Non-Goals

**Goals:**
- Two pull requests that update different roadmap items merge without conflicts.
- The content of the roadmap stays the same; only its shape changes.

**Non-Goals:**
- Reprioritizing, rewording or pruning items.
- Generating the roadmap from specs or release notes.
- Automating a check of the line-per-sentence rule.

## Decisions

**Semantic line breaks.** One sentence per line in both files. Markdown renders consecutive lines of a paragraph as one paragraph, so the published page doesn't change. Continuation lines of a list item are indented to the item's content, so they render inside it. A list item whose sentence enumerates independent follow-ups (the Flutter ones) becomes sub-bullets, so removing one doesn't touch the others.

**Direction as subsections.** A Markdown table row is one line by definition, so the table can't take line breaks. Each layer becomes a `###` subsection with its role, its status on its own line (`Status: **Available**.`) and its capabilities as bullets, one per stack or feature for Platforms. The runners paragraph becomes a subsection with one block per runner (Claude Code and Herdr, Orca), each with its own status line. Direction only summarizes: the detail of what works lives in `openspec/specs/` and the release notes, so a new capability of an existing bullet doesn't need to be added to it.

**Unnumbered planned sections.** Headings are `### Reliability on any machine`, and their order on the page is the priority. Removing or inserting a section touches only that section. The one cross-reference by number ("see point 2 above", in Rethinking Settings) becomes a link to the section's anchor.

**Rules in `AGENTS.md`.** The archive rule is where agents look when they update the roadmap, so it lists the merge-friendly rules there. `docs/process/openspec.md` gets one sentence for people, in both languages.

## Risks / Trade-offs

- [This pull request conflicts with every open one that touches the roadmap] → Mentioned in its body: merge it when few are open; resolving means re-applying their roadmap edit on the new shape.
- [Agents keep appending to existing lines out of habit] → The rule in `AGENTS.md` says so explicitly, and the new shape has no long line left to append to.
