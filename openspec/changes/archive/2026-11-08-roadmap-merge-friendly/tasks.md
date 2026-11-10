# Tasks

## 1. Roadmap

- [x] 1.1 Rewrite Direction in `docs/roadmap.md` and `docs/roadmap.es.md` as subsections per layer and runner, with a status line and one capability bullet per line, keeping every capability
- [x] 1.2 Remove the numbers from the Planned headings, reword the Planned intro to say the order is the priority, and turn "see point 2 above" into a link to the section in both languages
- [x] 1.3 Put one sentence per line in the rest of both files, and verify with `grep` that no `### [0-9]` heading or numbered cross-reference is left in `docs/`, `openspec/specs/`, `AGENTS.md` or `mkdocs.yml`

## 2. Rules

- [x] 2.1 Replace "renumber the remaining ones" in `AGENTS.md`'s archive rule with the merge-friendly rules
- [x] 2.2 Describe the roadmap update in `docs/process/openspec.md` and `openspec.es.md`

## 3. Check

- [x] 3.1 Verify `mkdocs build --strict` passes and the section links resolve in both languages
