# Tasks

## 1. Merge develop

- [ ] 1.1 Merge `origin/develop` (`16521a1e`) into `feature/ui-v2-develop-sync` with a merge commit, never a rebase
- [ ] 1.2 Resolve `NewChangeDialog.kt`, `WorktreeDetailPane.kt`, `WorktreeListPane.kt`, `WorktreeRowModel.kt` and `WorktreeScreen.kt` towards the epic's structure, keeping `develop`'s behaviour compiling (D1)
- [ ] 1.3 Keep `StatusSegmentsTest.kt` deleted
- [ ] 1.4 Combine both sides in `openspec/specs/multirepo-desktop/spec.md`, `docs/guide/desktop-app(.es).md` and `docs/roadmap(.es).md`
- [ ] 1.5 `./gradlew test` compiles and passes, then commit the merge

## 2. Orca multirepo actions through the catalog

- [ ] 2.1 Turn `ActionId.OpenMultirepoAgent` and `ActionId.OpenAllInHerdr` into `OpenMultirepoAgent(target)` and `OpenAll(target)`, keeping the Herdr keys (D2)
- [ ] 2.2 `ViewActionContext.orcaInstalled`; the view catalog declares the multirepo agent in Herdr and in Orca (hidden without Orca) and the overflow lists them before "Clean up"
- [ ] 2.3 `groupCatalog` declares "Open all in Herdr" and "Open all in Orca" (hidden without Orca, disabled while opening) (D3)
- [ ] 2.4 `WorktreeScreen` dispatches both multirepo agent actions from the ⋯ menu and drops `MultirepoAgentMenu`; `ChangeGroupHeaderView` renders the group catalog's open actions
- [ ] 2.5 Tests in `ActionSurfacesTest`: overflow with and without Orca, group catalog availability and order

## 3. Base and merged-into-base in the inspector and the row

- [ ] 3.1 `statusProperties` compares with `status.base` named by `baseName`, and shows "merged" keyed by the branch `mergedInto` returns (D4)
- [ ] 3.2 `InspectorModelsTest`: recorded base divergence, merged into the base, merged into the main branch but not the base (moving `develop`'s `StatusSegmentsTest` case here)
- [ ] 3.3 Check `WorktreeRowModel`'s Git cell and `develop`'s `WorktreeRowModelTest` cases pass on the v2 row

## 4. New change dialog

- [ ] 4.1 The v2 `NewChangeDialog` shows the runner selector after "Fetch first" and each repository's opening result next to its creation result (D5)
- [ ] 4.2 `multirepoNoticeMessage` covers the Orca notices

## 5. Docs and validation

- [ ] 5.1 Desktop app guide (EN/ES): Orca multirepo actions in the ⋯ menu and the group header, the base in the inspector
- [ ] 5.2 `./gradlew test` green
- [ ] 5.3 Run the app on the secondary display and check, in light and dark, the multirepo ⋯ menu, a change group header, the New change dialog and the inspector's Status section
- [ ] 5.4 `/opsx:archive`, updating `docs/roadmap.md` and `docs/roadmap.es.md` (drop the "Orca actions through the catalog" follow-up)
- [ ] 5.5 Implementation commit, then ask Ciro before opening the PR with `--base epic/ui-redesign-v2 --assignee CiroFeliu`
