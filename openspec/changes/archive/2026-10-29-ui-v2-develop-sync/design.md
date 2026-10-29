# Design

## Context

`feature/ui-v2-develop-sync` starts from `epic/ui-redesign-v2` at `30416613`, which already has the action catalog (`ui/actions`) and the inspector and row grid (`ui/worktree/inspector`, `WorktreeRowModel`). `origin/develop` at `16521a1e` adds `orca-multirepos` and `merged-against-base`. A trial merge conflicts in `NewChangeDialog`, `WorktreeDetailPane`, `WorktreeListPane`, `WorktreeRowModel`, `WorktreeScreen`, `StatusSegmentsTest` (deleted in the epic, modified in `develop`), the desktop app guide, the roadmap and the `multirepo-desktop` spec. Everything else (core, CLI, `WorktreeBoard`, `ProjectWorktrees`, `MultirepoActions`, strings, the other specs) merges cleanly.

## Goals / Non-Goals

**Goals:**
- Bring `develop` in with a merge commit whose conflicts resolve to the v2 structure.
- Route the Orca multirepo actions through the catalog (the follow-up recorded by `ui-v2-action-catalog`).
- Show the recorded base and merged-into-base in the inspector and the row.

**Non-Goals:**
- Hiding "Open all in Herdr" when Herdr isn't installed: the header has always shown it and that stays as it is.
- Moving "Add repository" or "Clean up group" into the catalog; the group header keeps those as they are.
- Any change to core, the CLI or `WorktreeBoard`'s behaviour from `develop`.

## Decisions

### D1. Merge first, adapt after
The merge commit resolves conflicts minimally towards the epic's files (taking the epic's side and re-applying `develop`'s intent where it is a small hunk, such as `gitCell`) so it compiles; the catalog routing and the inspector work land in the implementation commit. This keeps the merge reviewable as a merge and the adaptation as an ordinary diff.

### D2. Runner-parameterized multirepo action ids
`ActionId.OpenMultirepoAgent` and `ActionId.OpenAllInHerdr` become `OpenMultirepoAgent(target: OpenTarget)` and `OpenAll(target: OpenTarget)`, with keys `multirepo.open-agent.<runner>` and `multirepo.open-all.<runner>` (the Herdr keys stay the same). This mirrors `ActionId.Open(target)` for worktrees and avoids one id per runner. Alternative considered: separate `OpenAllInOrca`/`OpenMultirepoAgentInOrca` objects; rejected because every surface would then need parallel branches per runner.

### D3. A group catalog for change group headers
`groupCatalog(GroupActionContext, strings)` in `ui/actions` declares, per change group, "Open all in Herdr" (always available) and "Open all in Orca" (hidden without Orca, disabled with the "opening" reason while the group is being opened in Orca). `ChangeGroupHeaderView` renders the visible entries as its text buttons in catalog order instead of hard-coding them. `ViewActionContext` gains `orcaInstalled`, and the view overflow lists the multirepo agent in Herdr, then in Orca, then the destructive "Clean up". `OpenAll` no longer appears in the view catalog, since it acts on a group, not on the view.

### D4. One status row against the base
`statusProperties` shows a single "vs <branch>" row: when `mergedInto(mainBranchName)` names a branch, "vs <that branch>: merged"; otherwise the base divergence (`status.base`) keyed by `status.baseName ?: mainBranchName`. `base` defaults to `mainBranch` in `WorktreeStatus`, so a worktree without a recorded base reads as before. `mergedInto` stays where `develop` defines it (`WorktreeRowModel.kt`) and is shared by the row and the inspector. `develop`'s `statusSegments` change is dropped with the epic's removal of that function, and its test becomes an `InspectorModelsTest` case.

### D5. New change dialog
The epic's `NewChangeDialog` (`ShogunDialog`, `Field`, `FieldLabel`) gains, after "Fetch first", a `FieldLabel(openNewChangeIn)` with a `SegmentedSelector` over `change.runners` (only when not empty), and next to each repository's creation result its opening result, both taken from `develop`. `multirepoNoticeMessage` covers `develop`'s Orca notices.

### D6. Agent labels
Where `develop` touched the multirepo agent label in `WorktreeScreen`, the epic's `AgentIndicator` stays; `develop`'s `MultirepoAgentMenu` is removed because the ⋯ menu now carries both runners.

## Risks / Trade-offs

- [The merge commit alone may not satisfy every new spec] → it only has to compile and keep tests green; the implementation commit completes the behaviour and both ship in the same pull request.
- [`ActionId` keys used elsewhere (palette history, tests)] → the Herdr keys are unchanged, so nothing persisted changes.
- [Orca's group opening is slow] → the header's Orca action is disabled while it runs, as `develop`'s button was.
